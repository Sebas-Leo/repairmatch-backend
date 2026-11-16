package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.exception.IdentityProviderException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin Didit HTTP client: {@code POST {base}/v3/session/} with the {@code x-api-key} header. */
@Component
public class DiditClient {

    public record CreatedSession(String sessionId, String url, String status) {
    }

    private final DiditProperties properties;
    private final RestClient http;

    public DiditClient(DiditProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        // Warn (names only, never values) instead of failing: local runs and CI have no Didit config.
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) missing.add("DIDIT_API_KEY");
        if (properties.getWorkflowId() == null || properties.getWorkflowId().isBlank()) missing.add("DIDIT_WORKFLOW_ID");
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) missing.add("DIDIT_WEBHOOK_SECRET");
        if (!missing.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Didit verification disabled; missing: {}", missing);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The exact JSON fields Didit's create-session endpoint expects (snake_case). */
    Map<String, Object> sessionRequestBody(String vendorData) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", properties.getWorkflowId().trim());
        body.put("vendor_data", vendorData);
        // Force the hosted flow into Spanish (ISO 639-1) instead of browser detection.
        body.put("language", "es");
        if (properties.getCallbackUrl() != null && !properties.getCallbackUrl().isBlank()) {
            body.put("callback", properties.getCallbackUrl().trim());
        }
        return body;
    }

    /** Creates a hosted verification session; {@code vendorData} links it back to our user id. */
    public CreatedSession createSession(String vendorData) {
        // Serialized up front: SimpleClientHttpRequestFactory streams a Map body chunked (no
        // Content-Length) and Didit then reads it as empty ("workflow_id: This field is required").
        byte[] body = JSON.writeValueAsBytes(sessionRequestBody(vendorData));
        try {
            Map<?, ?> response = http.post()
                    .uri(properties.getBaseUrl() + "/v3/session/")
                    .header("x-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(body.length)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("session_id");
            Object url = response == null ? null : response.get("url");
            if (!(id instanceof String sessionId) || !(url instanceof String sessionUrl)) {
                throw new IdentityProviderException("El proveedor de verificación respondió de forma inesperada");
            }
            Object status = response.get("status");
            return new CreatedSession(sessionId, sessionUrl, status instanceof String s ? s : "Not Started");
        } catch (IdentityProviderException ex) {
            throw ex;
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // Didit's status and error body explain config problems (bad key, workflow, field); they hold no secrets.
            String detail = ex.getResponseBodyAsString();
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Didit session creation failed: HTTP {} {}",
                    ex.getStatusCode().value(), detail.length() > 500 ? detail.substring(0, 500) : detail);
            throw new IdentityProviderException("No pudimos iniciar la verificación. Inténtalo de nuevo en unos minutos");
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Didit session creation failed: {}", ex.getClass().getName());
            throw new IdentityProviderException("No pudimos iniciar la verificación. Inténtalo de nuevo en unos minutos");
        }
    }
}
