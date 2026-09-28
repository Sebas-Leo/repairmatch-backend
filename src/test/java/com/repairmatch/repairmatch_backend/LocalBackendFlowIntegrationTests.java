package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:local_flow;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class LocalBackendFlowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TechnicianRepository technicians;
    @Autowired ApplianceTypeRepository types;
    @Autowired RequestRepository requests;
    @Autowired ProposalRepository proposals;
    @Autowired ServiceRepository services;
    @Autowired ReviewRepository reviews;
    @Autowired JwtEncoder encoder;
    @Value("${security.jwt.issuer}") String issuer;

    @BeforeEach
    void clean() {
        reviews.deleteAll();
        services.deleteAll();
        proposals.deleteAll();
        requests.deleteAll();
        technicians.deleteAll();
        types.deleteAll();
        users.deleteAll();
    }

    @Test
    void proposalAcceptanceCreatesOneContractAndReviewUpdatesReputation() throws Exception {
        User owner = user(Role.CLIENT);
        User outsider = user(Role.CLIENT);
        ApplianceType type = type();
        User first = technician(type, -12.0464, -77.0428);
        User second = technician(type, -12.0464, -77.0428);
        Request request = request(owner, type);

        long firstProposal = submit(request, first);
        long secondProposal = submit(request, second);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CON_PROPUESTAS);

        mvc.perform(post("/api/proposals/{id}/accept", firstProposal).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        String accepted = mvc.perform(post("/api/proposals/{id}/accept", firstProposal).header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PROGRAMADO"))
                .andReturn().getResponse().getContentAsString();
        long serviceId = ((Number) JsonPath.read(accepted, "$.id")).longValue();
        assertThat(services.count()).isEqualTo(1);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CERRADA);
        assertThat(proposals.findById(firstProposal).orElseThrow().getStatus()).isEqualTo(Proposal.Status.ACCEPTED);
        assertThat(proposals.findById(secondProposal).orElseThrow().getStatus()).isEqualTo(Proposal.Status.REJECTED);

        mvc.perform(post("/api/proposals/{id}/accept", secondProposal).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Too early\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/services/{id}", serviceId).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/services/{id}/status?status=EN_ATENCION", serviceId).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/services/{id}/status?status=EN_ATENCION", serviceId).header("Authorization", bearer(first)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/services/{id}/status?status=COMPLETADO", serviceId).header("Authorization", bearer(first)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":1,\"comment\":\"Not mine\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Fixed\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"Again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/technicians/{id}/reputation", first.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(get("/api/technicians/{id}/reputation", second.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void incompatibleTechnicianCannotSubmitProposal() throws Exception {
        User owner = user(Role.CLIENT);
        User farAway = technician(type(), 0, 0);
        Request request = request(owner, types.findAll().get(0));
        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(farAway))
                .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isForbidden());
        assertThat(proposals.count()).isZero();
    }

    private long submit(Request request, User technician) throws Exception {
        String body = mvc.perform(post("/api/requests/{id}/proposals", request.getId())
                .header("Authorization", bearer(technician)).contentType(MediaType.APPLICATION_JSON)
                .content(proposalBody())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String proposalBody() {
        return "{\"diagnosticCost\":45.50,\"availableAt\":\"" + LocalDateTime.now().plusDays(2) +
                "\",\"conditions\":\"Diagnostic visit\"}";
    }

    private User user(Role role) {
        return users.save(new User("Test User", UUID.randomUUID() + "@example.com", "encoded", role));
    }

    private ApplianceType type() {
        return types.save(ApplianceType.builder().name("Appliance-" + UUID.randomUUID()).description("Test").build());
    }

    private User technician(ApplianceType type, double latitude, double longitude) {
        User user = user(Role.TECHNICIAN);
        Technician profile = new Technician(user);
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
        profile.setMaxRadiusKm(20.0);
        profile.setApplianceTypes(Set.of(type));
        technicians.save(profile);
        return user;
    }

    private Request request(User owner, ApplianceType type) {
        return requests.save(Request.builder().client(owner).applianceType(type)
                .originalDescription("Does not start").brand("Test").model("One")
                .latitude(-12.0464).longitude(-77.0428).build());
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", user.getRole().name()).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
