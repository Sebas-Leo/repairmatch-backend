package com.repairmatch.repairmatch_backend.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DiditSessionRequestTest {

    private DiditClient client(String workflowId, String callback) {
        DiditProperties properties = new DiditProperties();
        properties.setApiKey("test-key");
        properties.setWorkflowId(workflowId);
        properties.setCallbackUrl(callback);
        return new DiditClient(properties);
    }

    @Test
    void serializedBodyCarriesSnakeCaseFieldsWithValues() {
        Map<String, Object> body = client(" wf-123 ", "https://web.example/verification/return")
                .sessionRequestBody("42");

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(body));

        assertThat(json.path("workflow_id").asText()).isEqualTo("wf-123");
        assertThat(json.path("vendor_data").asText()).isEqualTo("42");
        assertThat(json.path("language").asText()).isEqualTo("es");
        assertThat(json.path("callback").asText()).isEqualTo("https://web.example/verification/return");
    }

    @Test
    void callbackIsOmittedWhenNotConfigured() {
        Map<String, Object> body = client("wf-123", "").sessionRequestBody("42");

        assertThat(body).containsKey("workflow_id").doesNotContainKey("callback");
    }
}
