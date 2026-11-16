package com.repairmatch.repairmatch_backend.identity;

import com.repairmatch.repairmatch_backend.exception.IdentityProviderException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    }

    /** Creates a hosted verification session; {@code vendorData} links it back to our user id. */
    public CreatedSession createSession(String vendorData) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", properties.getWorkflowId());
        body.put("vendor_data", vendorData);
        if (properties.getCallbackUrl() != null && !properties.getCallbackUrl().isBlank()) {
            body.put("callback", properties.getCallbackUrl());
        }
        try {
            Map<?, ?> response = http.post()
                    .uri(properties.getBaseUrl() + "/v3/session/")
                    .header("x-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
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
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Didit session creation failed: {}", ex.getClass().getName());
            throw new IdentityProviderException("No pudimos iniciar la verificación. Inténtalo de nuevo en unos minutos");
        }
    }
}
