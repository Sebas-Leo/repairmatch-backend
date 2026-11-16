package com.repairmatch.repairmatch_backend.identity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Didit settings, bound from {@code didit.*} (env vars DIDIT_API_KEY, DIDIT_WORKFLOW_ID, DIDIT_WEBHOOK_SECRET, DIDIT_CALLBACK_URL). */
@Component
@ConfigurationProperties(prefix = "didit")
@Getter
@Setter
public class DiditProperties {
    private String baseUrl = "https://verification.didit.me";
    private String apiKey = "";
    private String workflowId = "";
    private String webhookSecret = "";
    private String callbackUrl = "";

    public boolean canCreateSessions() {
        return notBlank(apiKey) && notBlank(workflowId);
    }

    public boolean canVerifyWebhooks() {
        return notBlank(webhookSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
