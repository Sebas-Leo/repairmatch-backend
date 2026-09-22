package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Proposal;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.ApplianceTypeRepository;
import com.repairmatch.repairmatch_backend.repository.ProposalRepository;
import com.repairmatch.repairmatch_backend.repository.RequestRepository;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProposalComparisonIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private ApplianceTypeRepository applianceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @BeforeEach
    void cleanDatabase() {
        proposalRepository.deleteAll();
        requestRepository.deleteAll();
        applianceTypeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCompareProposalsOrderedByDiagnosticCost() throws Exception {
        User owner = saveUser("Client Owner", Role.CLIENT);
        User firstTechnician = saveUser("First Technician", Role.TECHNICIAN);
        User secondTechnician = saveUser("Second Technician", Role.TECHNICIAN);
        Request request = saveRequest(owner);

        Proposal expensive = proposalRepository.save(new Proposal(
                request,
                firstTechnician,
                new BigDecimal("80.00"),
                LocalDateTime.now().plusDays(1),
                "Includes diagnosis"
        ));
        Proposal affordable = proposalRepository.save(new Proposal(
                request,
                secondTechnician,
                new BigDecimal("45.50"),
                LocalDateTime.now().plusDays(2),
                "Visit only"
        ));

        mockMvc.perform(get("/api/requests/{requestId}/proposals", request.getId())
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(affordable.getId()))
                .andExpect(jsonPath("$[0].technicianId").value(secondTechnician.getId().toString()))
                .andExpect(jsonPath("$[0].technicianName").value("Second Technician"))
                .andExpect(jsonPath("$[0].diagnosticCost").value(45.50))
                .andExpect(jsonPath("$[1].id").value(expensive.getId()))
                .andExpect(jsonPath("$[1].diagnosticCost").value(80.00))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void nonOwnerCannotCompareProposals() throws Exception {
        User owner = saveUser("Client Owner", Role.CLIENT);
        User anotherClient = saveUser("Another Client", Role.CLIENT);
        Request request = saveRequest(owner);

        mockMvc.perform(get("/api/requests/{requestId}/proposals", request.getId())
                        .header("Authorization", "Bearer " + tokenFor(anotherClient)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tiene autorización para consultar las propuestas de esta solicitud"));
    }

    @Test
    void anonymousUserCannotCompareProposals() throws Exception {
        User owner = saveUser("Client Owner", Role.CLIENT);
        Request request = saveRequest(owner);

        mockMvc.perform(get("/api/requests/{requestId}/proposals", request.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingRequestReturnsNotFound() throws Exception {
        User owner = saveUser("Client Owner", Role.CLIENT);

        mockMvc.perform(get("/api/requests/{requestId}/proposals", 999_999L)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La solicitud especificada no existe"));
    }

    private User saveUser(String name, Role role) {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.save(new User(name, email, "encoded-password", role));
    }

    private Request saveRequest(User owner) {
        ApplianceType applianceType = applianceTypeRepository.save(
                ApplianceType.builder()
                        .name("Appliance-" + UUID.randomUUID())
                        .description("Test appliance")
                        .build()
        );

        return requestRepository.save(
                Request.builder()
                        .client(owner)
                        .applianceType(applianceType)
                        .originalDescription("Does not turn on")
                        .brand("RepairMatch")
                        .model("Test")
                        .build()
        );
    }

    private String tokenFor(User user) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", user.getRole().name())
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
