package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Evidence;
import com.repairmatch.repairmatch_backend.model.EvidenceId;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.repository.EvidenceRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired RequestRepository requestRepository;
    @Autowired EvidenceRepository evidenceRepository;
    @Autowired ApplianceTypeRepository applianceTypeRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtEncoder jwtEncoder;
    @Value("${security.jwt.issuer}") String issuer;

    @BeforeEach
    void cleanDatabase() {
        evidenceRepository.deleteAll();
        requestRepository.deleteAll();
        applianceTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void clientCanCreateAndReadOwnRequest() throws Exception {
        User client = saveUser(Role.CLIENT);
        ApplianceType type = saveApplianceType();

        String response = mockMvc.perform(post("/api/requests")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applianceTypeId": %d,
                                  "originalDescription": "Does not turn on",
                                  "brand": "RepairMatch",
                                  "model": "RM-1"
                                }
                                """.formatted(type.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(client.getId().toString()))
                .andExpect(jsonPath("$.applianceTypeName").value(type.getName()))
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andReturn().getResponse().getContentAsString();

        Long requestId = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/api/requests/{requestId}", requestId)
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId));

        mockMvc.perform(get("/api/requests")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId));
    }

    @Test
    void technicianCannotCreateRequest() throws Exception {
        User technician = saveUser(Role.TECHNICIAN);
        ApplianceType type = saveApplianceType();

        mockMvc.perform(post("/api/requests")
                        .header("Authorization", bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applianceTypeId": %d, "originalDescription": "Broken"}
                                """.formatted(type.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo los clientes pueden publicar solicitudes"));
    }

    @Test
    void nonOwnerCannotReadRequestOrEvidence() throws Exception {
        User owner = saveUser(Role.CLIENT);
        User anotherClient = saveUser(Role.CLIENT);
        Request request = saveRequest(owner);
        evidenceRepository.save(Evidence.builder()
                .id(new EvidenceId(request.getId(), 1))
                .request(request)
                .mediaUrl("https://example.com/evidence.jpg")
                .mediaType("image/jpeg")
                .build());

        mockMvc.perform(get("/api/requests/{requestId}", request.getId())
                        .header("Authorization", bearer(anotherClient)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/requests/{requestId}/evidences", request.getId())
                        .header("Authorization", bearer(anotherClient)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequestOperationsReturnDocumentedClientErrors() throws Exception {
        User owner = saveUser(Role.CLIENT);
        Request request = saveRequest(owner);

        mockMvc.perform(patch("/api/requests/{requestId}/close", request.getId())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/requests/{requestId}", 999_999L)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    private User saveUser(Role role) {
        return userRepository.save(new User(
                role.name(), UUID.randomUUID() + "@example.com", "encoded-password", role));
    }

    private ApplianceType saveApplianceType() {
        return applianceTypeRepository.save(ApplianceType.builder()
                .name("Type-" + UUID.randomUUID())
                .description("Test appliance")
                .build());
    }

    private Request saveRequest(User owner) {
        return requestRepository.save(Request.builder()
                .client(owner)
                .applianceType(saveApplianceType())
                .originalDescription("Broken appliance")
                .build());
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", user.getRole().name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return "Bearer " + token;
    }
}
