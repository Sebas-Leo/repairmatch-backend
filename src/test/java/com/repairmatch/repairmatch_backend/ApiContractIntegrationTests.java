package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Frontend-facing contract: one JSON error shape, CORS preflight, ISO dates. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:api_contract;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ApiContractIntegrationTests {
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ApplianceTypeRepository types;
    @Autowired RequestRepository requests;
    @Autowired JwtEncoder encoder;
    @Value("${security.jwt.issuer}") String issuer;

    @BeforeEach
    void clean() {
        requests.deleteAll();
        types.deleteAll();
        users.deleteAll();
    }

    @Test
    void preflightIsAllowedForConfiguredOriginWithoutToken() throws Exception {
        mvc.perform(options("/api/requests/1/cancel").header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsStringIgnoringCase("Authorization"),
                                org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type"))))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightIsRejectedForUnknownOrigin() throws Exception {
        mvc.perform(options("/api/requests").header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenReturnsJson401WithMessage() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/requests"));
    }

    @Test
    void garbageBearerTokenReturnsJson401() throws Exception {
        mvc.perform(get("/api/requests").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void wrongRoleReturnsJson403() throws Exception {
        User technician = user(Role.TECHNICIAN);
        mvc.perform(get("/api/requests").header("Authorization", bearer(technician)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownPathMethodAndMediaTypeKeepTheirStatusWithJsonBody() throws Exception {
        User client = user(Role.CLIENT);
        mvc.perform(get("/api/does-not-exist").header("Authorization", bearer(client)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(delete("/api/requests").header("Authorization", bearer(client)))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/requests").header("Authorization", bearer(client))
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void malformedJsonAndValidationReturnMessageAndFieldErrors() throws Exception {
        User client = user(Role.CLIENT);
        mvc.perform(post("/api/requests").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/requests").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"originalDescription\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors.applianceTypeId").isNotEmpty())
                .andExpect(jsonPath("$.errors.originalDescription").isNotEmpty());
    }

    @Test
    void invalidEnumQueryParameterIsA400NamingTheParameter() throws Exception {
        User client = user(Role.CLIENT);
        mvc.perform(patch("/api/services/1/status?status=BOGUS").header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("status")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("EN_ATENCION")));
        mvc.perform(patch("/api/services/1/status").header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").isNotEmpty());
    }

    @Test
    void createdResourcesUseIsoDatesAndEvidenceUrlMustBeHttp() throws Exception {
        User client = user(Role.CLIENT);
        ApplianceType type = types.save(ApplianceType.builder().name("Fridge-" + UUID.randomUUID()).description("t").build());
        String created = mvc.perform(post("/api/requests").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applianceTypeId\":" + type.getId() + ",\"originalDescription\":\"Does not cool\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")))
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        mvc.perform(post("/api/requests/{id}/evidences", id).header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaUrl\":\"javascript:alert(1)\",\"mediaType\":\"image/jpeg\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.mediaUrl").isNotEmpty());
        mvc.perform(post("/api/requests/{id}/evidences", id).header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaUrl\":\"https://example.com/a.jpg\",\"mediaType\":\"image/jpeg\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.evidenceNumber").value(1));
    }

    @Test
    void openApiDocumentIsPublishedInDefaultProfile() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").isNotEmpty());
    }

    private User user(Role role) {
        return users.save(new User("Test User", UUID.randomUUID() + "@example.com", "encoded", role));
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", user.getRole().name()).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
