package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared fixtures for the end-to-end API tests: real security chain, H2 database, data prepared through repositories. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:backend_completion;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
abstract class BackendTestSupport {
    protected static final double LAT = -12.0464;
    protected static final double LON = -77.0428;

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected TechnicianRepository technicians;
    @Autowired protected ApplianceTypeRepository types;
    @Autowired protected RequestRepository requests;
    @Autowired protected EvidenceRepository evidences;
    @Autowired protected ProposalRepository proposals;
    @Autowired protected ServiceRepository services;
    @Autowired protected ReviewRepository reviews;
    @Autowired protected JwtEncoder encoder;
    @Value("${security.jwt.issuer}") protected String issuer;

    @BeforeEach
    void cleanDatabase() {
        reviews.deleteAll();
        services.deleteAll();
        proposals.deleteAll();
        evidences.deleteAll();
        requests.deleteAll();
        technicians.deleteAll();
        types.deleteAll();
        users.deleteAll();
    }

    protected User user(Role role) {
        return users.save(new User("Test " + role.name().toLowerCase(), UUID.randomUUID() + "@example.com", "encoded", role));
    }

    protected ApplianceType type() {
        return types.save(ApplianceType.builder().name("Appliance-" + UUID.randomUUID()).description("Test").build());
    }

    /** A configured technician serving {@code type} within 20 km of the given point. */
    protected User technician(ApplianceType type, double latitude, double longitude) {
        return technician(type, latitude, longitude, 20.0);
    }

    protected User technician(ApplianceType type, double latitude, double longitude, double radiusKm) {
        User user = user(Role.TECHNICIAN);
        Technician profile = new Technician(user);
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
        profile.setMaxRadiusKm(radiusKm);
        profile.setApplianceTypes(Set.of(type));
        technicians.save(profile);
        return user;
    }

    protected Request request(User owner, ApplianceType type) {
        return requests.save(Request.builder().client(owner).applianceType(type)
                .originalDescription("Does not start").brand("Test").model("One")
                .latitude(LAT).longitude(LON).build());
    }

    protected String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("role", user.getRole().name()).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String proposalBody() {
        return "{\"diagnosticCost\":45.50,\"availableAt\":\"" + LocalDateTime.now().plusDays(2) + "\",\"conditions\":\"Diagnostic visit\"}";
    }

    /** Submits a valid proposal through the API and returns its id. */
    protected long submit(Request request, User technician) throws Exception {
        String body = mvc.perform(post("/api/requests/{id}/proposals", request.getId())
                        .header("Authorization", bearer(technician)).contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return id(body);
    }

    /** Accepts a proposal through the API as the request owner and returns the created service id. */
    protected long accept(long proposalId, User owner) throws Exception {
        String body = mvc.perform(post("/api/proposals/{id}/accept", proposalId).header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return id(body);
    }

    protected void advance(long serviceId, User actor, String status) throws Exception {
        mvc.perform(patch("/api/services/{id}/status", serviceId).param("status", status).header("Authorization", bearer(actor)))
                .andExpect(status().isOk());
    }

    /** request -> proposal -> accepted -> EN_ATENCION -> COMPLETADO; returns the service id. */
    protected long completedService(Request request, User owner, User technician) throws Exception {
        long serviceId = accept(submit(request, technician), owner);
        advance(serviceId, technician, "EN_ATENCION");
        advance(serviceId, technician, "COMPLETADO");
        return serviceId;
    }

    protected static long id(String body) {
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
