package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.identity.DiditClient;
import com.repairmatch.repairmatch_backend.identity.DiditProperties;
import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.IdentityStatus;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Didit identity verification: session creation, signed webhook, and where the badge flag is exposed. The Didit HTTP call is mocked. */
@TestPropertySource(properties = {
        "didit.api-key=test-key", "didit.workflow-id=test-workflow",
        "didit.webhook-secret=" + DiditIdentityIntegrationTests.SECRET, "didit.callback-url=https://app.example/verification/return"})
class DiditIdentityIntegrationTests extends BackendTestSupport {

    static final String SECRET = "whsec_integration";
    private static final String WEBHOOK = "/api/verification/identity/webhook";
    private static final String SESSION_URL = "https://verify.didit.me/session/abc";

    @MockitoBean DiditClient didit;
    @Autowired DiditProperties properties;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void restoreConfig() {
        properties.setApiKey("test-key");
        properties.setWebhookSecret(SECRET);
    }

    private void diditReturns(String sessionId) {
        when(didit.createSession(anyString())).thenReturn(new DiditClient.CreatedSession(sessionId, SESSION_URL, "Not Started"));
    }

    private void startSession(User user, String sessionId) throws Exception {
        diditReturns(sessionId);
        mvc.perform(post("/api/verification/identity/session").header("Authorization", bearer(user)))
                .andExpect(status().isCreated());
    }

    private static String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String event(String sessionId, String diditStatus, User user) {
        return "{\"webhook_type\":\"status.updated\",\"session_id\":\"" + sessionId + "\",\"status\":\"" + diditStatus
                + "\",\"vendor_data\":\"" + user.getId() + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions webhook(String body, String signature, long timestamp) throws Exception {
        var request = post(WEBHOOK).contentType(APPLICATION_JSON).content(body)
                .header("X-Timestamp", String.valueOf(timestamp));
        if (signature != null) request.header("X-Signature", signature);
        return mvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions signedWebhook(String body) throws Exception {
        return webhook(body, hmac(body, SECRET), Instant.now().getEpochSecond());
    }

    private void identityIs(User user, String expected) throws Exception {
        mvc.perform(get("/api/verification/identity").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
        // The /status alias must answer the same.
        mvc.perform(get("/api/verification/identity/status").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
    }

    // --- session creation -------------------------------------------------------------------

    @Test
    void anyRoleCanStartAVerificationAndGetsTheHostedUrl() throws Exception {
        for (Role role : Role.values()) {
            User user = user(role);
            diditReturns("sess-" + role);
            mvc.perform(post("/api/verification/identity/session").header("Authorization", bearer(user)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.url").value(SESSION_URL))
                    .andExpect(jsonPath("$.status").value("PENDING"));
            identityIs(user, "PENDING");
            verify(didit).createSession(user.getId().toString());
        }
    }

    @Test
    void statusStartsAsNotStartedAndTheEndpointsNeedAToken() throws Exception {
        User user = user(Role.CLIENT);
        identityIs(user, "NOT_STARTED");
        mvc.perform(get("/api/verification/identity")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/verification/identity/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAlreadyVerifiedUserGets409AndDiditIsNotCalled() throws Exception {
        User user = user(Role.TECHNICIAN);
        user.setIdentityStatus(IdentityStatus.VERIFIED);
        users.save(user);

        mvc.perform(post("/api/verification/identity/session").header("Authorization", bearer(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").exists());
        verify(didit, never()).createSession(anyString());
    }

    @Test
    void missingDiditConfigurationAnswers503WithTheStandardErrorShape() throws Exception {
        properties.setApiKey("");
        User user = user(Role.CLIENT);

        mvc.perform(post("/api/verification/identity/session").header("Authorization", bearer(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.path").value("/api/verification/identity/session"))
                .andExpect(jsonPath("$.message").exists());
        verify(didit, never()).createSession(anyString());
        identityIs(user, "NOT_STARTED");
    }

    @Test
    void aFailingProviderAnswers502AndLeavesTheStatusUntouched() throws Exception {
        when(didit.createSession(anyString())).thenThrow(
                new com.repairmatch.repairmatch_backend.exception.IdentityProviderException("down"));
        User user = user(Role.CLIENT);

        mvc.perform(post("/api/verification/identity/session").header("Authorization", bearer(user)))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
        identityIs(user, "NOT_STARTED");
    }

    // --- webhook ------------------------------------------------------------------------------

    @Test
    void aSignedApprovalVerifiesTheUserAndIsIdempotent() throws Exception {
        User user = user(Role.TECHNICIAN);
        startSession(user, "sess-1");
        String body = event("sess-1", "Approved", user);

        signedWebhook(body).andExpect(status().isOk());
        identityIs(user, "VERIFIED");
        String verifiedAt = users.findById(user.getId()).orElseThrow().getIdentityVerifiedAt().toString();

        signedWebhook(body).andExpect(status().isOk());
        identityIs(user, "VERIFIED");
        org.junit.jupiter.api.Assertions.assertEquals(verifiedAt,
                users.findById(user.getId()).orElseThrow().getIdentityVerifiedAt().toString());
        mvc.perform(get("/api/verification/identity").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.verifiedAt").value(not(nullValue())));
    }

    @Test
    void invalidOrMissingSignaturesAreRejectedWith401AndChangeNothing() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-2");
        String body = event("sess-2", "Approved", user);
        long now = Instant.now().getEpochSecond();

        webhook(body, hmac(body, "wrong-secret"), now).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        webhook(body, null, now).andExpect(status().isUnauthorized());
        webhook(body, hmac(body, SECRET), now - 1000).andExpect(status().isUnauthorized());
        webhook(body.replace("Approved", "Declined"), hmac(body, SECRET), now).andExpect(status().isUnauthorized());
        identityIs(user, "PENDING");
    }

    @Test
    void theWebhookIsReachableWithoutABearerToken() throws Exception {
        // No Authorization header at all: a bad signature must answer 401 from our check, not an auth wall,
        // and the error body is the standard contract shape.
        webhook("{}", "00", Instant.now().getEpochSecond())
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Firma del webhook inválida"));
    }

    @Test
    void anUnconfiguredWebhookSecretAnswers503() throws Exception {
        properties.setWebhookSecret("");
        webhook("{}", "00", Instant.now().getEpochSecond()).andExpect(status().isServiceUnavailable());
    }

    @Test
    void declinedBecomesRejectedAndTheUserCanTryAgain() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-3");
        signedWebhook(event("sess-3", "Declined", user)).andExpect(status().isOk());
        identityIs(user, "REJECTED");

        startSession(user, "sess-4");
        identityIs(user, "PENDING");
    }

    @Test
    void anExpiredOrAbandonedSessionGoesBackToNotStarted() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-5");
        signedWebhook(event("sess-5", "Abandoned", user)).andExpect(status().isOk());
        identityIs(user, "NOT_STARTED");
    }

    @Test
    void eventsForAnOldSessionOrAnUnknownUserAreAcknowledgedButIgnored() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-new");
        signedWebhook(event("sess-old", "Approved", user)).andExpect(status().isOk());
        identityIs(user, "PENDING");

        String unknown = "{\"webhook_type\":\"status.updated\",\"session_id\":\"ghost\",\"status\":\"Approved\",\"vendor_data\":\""
                + UUID.randomUUID() + "\"}";
        signedWebhook(unknown).andExpect(status().isOk());
    }

    @Test
    void aLateInProgressEventNeverUndoesAnApproval() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-6");
        signedWebhook(event("sess-6", "Approved", user)).andExpect(status().isOk());
        signedWebhook(event("sess-6", "In Progress", user)).andExpect(status().isOk());
        identityIs(user, "VERIFIED");
    }

    @Test
    void theUserIsFoundBySessionIdWhenVendorDataIsMissing() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-7");
        signedWebhook("{\"webhook_type\":\"status.updated\",\"session_id\":\"sess-7\",\"status\":\"Approved\"}")
                .andExpect(status().isOk());
        identityIs(user, "VERIFIED");
    }

    // --- profile name vs document name ----------------------------------------------------------

    private static String approvedWithDocument(String sessionId, User user, String first, String last) {
        return "{\"webhook_type\":\"status.updated\",\"session_id\":\"" + sessionId + "\",\"status\":\"Approved\","
                + "\"vendor_data\":\"" + user.getId() + "\",\"decision\":{\"status\":\"Approved\",\"id_verifications\":["
                + "{\"node_id\":\"n1\",\"status\":\"Approved\",\"first_name\":\"" + first + "\",\"last_name\":\"" + last + "\"}]}}";
    }

    private User namedUser(Role role, String name) {
        User user = user(role);
        user.setName(name);
        return users.save(user);
    }

    @Test
    void aMatchingDocumentNameSetsTheFlagToTrue() throws Exception {
        User user = namedUser(Role.CLIENT, "Ana Mar\u00eda P\u00e9rez");
        startSession(user, "sess-m1");
        signedWebhook(approvedWithDocument("sess-m1", user, "MARIA ANA", "PEREZ GOMEZ")).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, users.findById(user.getId()).orElseThrow().getIdentityNameMatches());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.identityVerified").value(true))
                .andExpect(jsonPath("$.identityNameMatches").value(true));
        mvc.perform(get("/api/verification/identity").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.nameMatches").value(true));
    }

    @Test
    void aDifferentDocumentNameSetsTheFlagToFalse() throws Exception {
        User user = namedUser(Role.CLIENT, "Carlos Quispe");
        startSession(user, "sess-m2");
        signedWebhook(approvedWithDocument("sess-m2", user, "Luis", "Ramos")).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.identityVerified").value(true))
                .andExpect(jsonPath("$.identityNameMatches").value(false));
        mvc.perform(get("/api/verification/identity").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.nameMatches").value(false));
    }

    @Test
    void aFullNameFieldIsUsedWhenPresent() throws Exception {
        User user = namedUser(Role.CLIENT, "Carmen Espa\u00f1ola");
        startSession(user, "sess-m3");
        signedWebhook("{\"session_id\":\"sess-m3\",\"status\":\"Approved\",\"vendor_data\":\"" + user.getId()
                + "\",\"decision\":{\"id_verifications\":[{\"full_name\":\"Carmen Espa\u00f1ola Espa\u00f1ola\"}]}}")
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, users.findById(user.getId()).orElseThrow().getIdentityNameMatches());
    }

    @Test
    void aMissingDocumentNameLeavesTheFlagNull() throws Exception {
        User user = namedUser(Role.CLIENT, "Carlos Quispe");
        startSession(user, "sess-m4");
        signedWebhook(event("sess-m4", "Approved", user)).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(users.findById(user.getId()).orElseThrow().getIdentityNameMatches());
        identityIs(user, "VERIFIED");
    }

    @Test
    void theFlagIsClearedWhenVerificationIsLost() throws Exception {
        User user = namedUser(Role.CLIENT, "Carlos Quispe");
        startSession(user, "sess-m5");
        signedWebhook(approvedWithDocument("sess-m5", user, "Carlos", "Quispe")).andExpect(status().isOk());
        signedWebhook(event("sess-m5", "Declined", user)).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(users.findById(user.getId()).orElseThrow().getIdentityNameMatches());
    }

    @Test
    void theDocumentNameIsNeverPersisted() throws Exception {
        User user = namedUser(Role.CLIENT, "Zed Quillfeather");
        startSession(user, "sess-m6");
        signedWebhook(approvedWithDocument("sess-m6", user, "Zed", "Quillfeather Zorbanz")).andExpect(status().isOk());

        // Every column of the user row, as text, must be free of the document-only token.
        java.util.Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?", user.getId());
        for (Object value : row.values()) {
            org.junit.jupiter.api.Assertions.assertFalse(String.valueOf(value).toLowerCase().contains("zorbanz"), String.valueOf(value));
        }
        // And no other table holds it either.
        for (String table : jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'PUBLIC'", String.class)) {
            for (java.util.Map<String, Object> r : jdbc.queryForList("select * from \"" + table + "\"")) {
                for (Object value : r.values()) {
                    org.junit.jupiter.api.Assertions.assertFalse(String.valueOf(value).toLowerCase().contains("zorbanz"), table);
                }
            }
        }
    }

    @Test
    void theNameFlagIsExposedWhereverIdentityVerifiedIs() throws Exception {
        ApplianceType fridge = type();
        User owner = namedUser(Role.CLIENT, "Rosa Flores");
        User tech = technician(fridge, LAT, LON);
        tech.setName("Juan Torres");
        users.save(tech);
        startSession(tech, "sess-x1");
        signedWebhook(approvedWithDocument("sess-x1", tech, "Torres", "Juan Perez")).andExpect(status().isOk());
        startSession(owner, "sess-x2");
        signedWebhook(approvedWithDocument("sess-x2", owner, "Maria", "Lopez")).andExpect(status().isOk());
        Request request = request(owner, fridge);
        submit(request, tech);
        Request open = request(owner, fridge); // not applied to yet, so it also shows in recommended-requests

        mvc.perform(get("/api/technicians/{id}", tech.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.identityNameMatches").value(true));
        mvc.perform(get("/api/technicians/me/profile").header("Authorization", bearer(tech)))
                .andExpect(jsonPath("$.identityNameMatches").value(true));
        mvc.perform(get("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$[0].identityNameMatches").value(true));
        mvc.perform(get("/api/requests/{id}/proposals/recommended", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$[0].proposal.identityNameMatches").value(true));
        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(tech)))
                .andExpect(jsonPath("$.content[0].identityNameMatches").value(true));
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(tech)))
                .andExpect(jsonPath("$.content[?(@.requestId==" + request.getId() + ")].clientIdentityNameMatches").value(false));
        mvc.perform(get("/api/technicians/me/recommended-requests").header("Authorization", bearer(tech)))
                .andExpect(jsonPath("$.content[?(@.request.requestId==" + open.getId() + ")].request.clientIdentityNameMatches").value(false));
    }

    // --- where the badge flag shows up ---------------------------------------------------------

    @Test
    void identityVerifiedIsExposedOnMeProfilesProposalsAndOpportunities() throws Exception {
        ApplianceType fridge = type();
        User owner = user(Role.CLIENT);
        User verifiedTech = technician(fridge, LAT, LON);
        User plainTech = technician(fridge, LAT, LON);
        startSession(verifiedTech, "sess-t");
        signedWebhook(event("sess-t", "Approved", verifiedTech)).andExpect(status().isOk());
        startSession(owner, "sess-c");
        signedWebhook(event("sess-c", "Approved", owner)).andExpect(status().isOk());

        // /users/me
        mvc.perform(get("/api/users/me").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.identityVerified").value(true));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(plainTech)))
                .andExpect(jsonPath("$.identityVerified").value(false));

        // technician profiles (public and own)
        mvc.perform(get("/api/technicians/{id}", verifiedTech.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));
        mvc.perform(get("/api/technicians/{id}", plainTech.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.identityVerified").value(false));
        mvc.perform(get("/api/technicians/me/profile").header("Authorization", bearer(verifiedTech)))
                .andExpect(jsonPath("$.identityVerified").value(true));

        // proposals: plain list, recommended order and the technician's own list
        Request request = request(owner, fridge);
        submit(request, verifiedTech);
        submit(request, plainTech);
        mvc.perform(get("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.technicianId=='" + verifiedTech.getId() + "')].identityVerified").value(true))
                .andExpect(jsonPath("$[?(@.technicianId=='" + plainTech.getId() + "')].identityVerified").value(false));
        mvc.perform(get("/api/requests/{id}/proposals/recommended", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$[?(@.proposal.technicianId=='" + verifiedTech.getId() + "')].proposal.identityVerified").value(true))
                .andExpect(jsonPath("$[?(@.proposal.technicianId=='" + plainTech.getId() + "')].proposal.identityVerified").value(false));
        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(verifiedTech)))
                .andExpect(jsonPath("$.content[0].identityVerified").value(true));
    }

    @Test
    void techniciansSeeWhetherTheClientBehindARequestIsVerified() throws Exception {
        ApplianceType fridge = type();
        User verifiedClient = user(Role.CLIENT);
        User plainClient = user(Role.CLIENT);
        User tech = technician(fridge, LAT, LON);
        startSession(verifiedClient, "sess-v");
        signedWebhook(event("sess-v", "Approved", verifiedClient)).andExpect(status().isOk());
        Request fromVerified = request(verifiedClient, fridge);
        Request fromPlain = request(plainClient, fridge);

        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(tech)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.requestId==" + fromVerified.getId() + ")].clientIdentityVerified").value(true))
                .andExpect(jsonPath("$.content[?(@.requestId==" + fromPlain.getId() + ")].clientIdentityVerified").value(false));
        mvc.perform(get("/api/technicians/me/recommended-requests").header("Authorization", bearer(tech)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.request.requestId==" + fromVerified.getId() + ")].request.clientIdentityVerified").value(true))
                .andExpect(jsonPath("$.content[?(@.request.requestId==" + fromPlain.getId() + ")].request.clientIdentityVerified").value(false));
    }

    @Test
    void noSensitiveVerificationDataLeaksIntoResponses() throws Exception {
        User user = user(Role.CLIENT);
        startSession(user, "sess-secret");
        signedWebhook(event("sess-secret", "Approved", user)).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.identitySessionId").doesNotExist())
                .andExpect(jsonPath("$.identityStatus").doesNotExist());
        mvc.perform(get("/api/verification/identity").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.status", is("VERIFIED")))
                .andExpect(jsonPath("$.identitySessionId").doesNotExist());
    }
}
