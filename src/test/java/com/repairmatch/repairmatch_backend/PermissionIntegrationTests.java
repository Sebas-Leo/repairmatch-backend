package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.*;
import com.repairmatch.repairmatch_backend.service.JwtService;
import com.repairmatch.repairmatch_backend.service.RequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RequestRepository requests;
    @Autowired EvidenceRepository evidences;
    @Autowired ApplianceTypeRepository types;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService tokens;
    @Autowired RequestService requestService;
    private User owner;
    private User other;
    private User technician;
    private Request request;
    private String ownerToken;
    private String otherToken;
    private String technicianToken;

    @BeforeEach
    void fixtures() {
        owner = account(Role.CLIENT);
        other = account(Role.CLIENT);
        technician = account(Role.TECHNICIAN);
        ownerToken = tokens.generateToken(owner).getAccessToken();
        otherToken = tokens.generateToken(other).getAccessToken();
        technicianToken = tokens.generateToken(technician).getAccessToken();
        ApplianceType type = types.saveAndFlush(ApplianceType.builder()
                .name("Test-" + UUID.randomUUID()).build());
        request = requests.saveAndFlush(Request.builder().client(owner).applianceType(type)
                .originalDescription("Solicitud privada de prueba")
                .status(Request.RequestStatus.CON_PROPUESTAS).build());
        evidences.saveAndFlush(Evidence.builder().id(new EvidenceId(request.getId(), 1))
                .request(request).mediaUrl("https://example.com/private.jpg").mediaType("image/jpeg").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "add", "cancel", "close"})
    void rejectsOtherClientWithoutChangingResources(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + otherToken)
                        .param("currentUserId", owner.getId().toString())
                        .param("clientId", owner.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private.jpg"))));
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "add", "cancel", "close"})
    void rejectsTechnician(String operation) throws Exception {
        mvc.perform(operation(operation).header("Authorization", "Bearer " + technicianToken))
                .andExpect(status().isForbidden());
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "add", "cancel", "close"})
    void rejectsUnauthenticatedAccess(String operation) throws Exception {
        mvc.perform(operation(operation)).andExpect(status().isUnauthorized());
        unchanged();
    }

    @Test
    void ownerCanReadAndAddEvidence() throws Exception {
        mvc.perform(operation("read").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].mediaUrl")
                        .value("https://example.com/private.jpg"));
        mvc.perform(operation("add").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestId").value(request.getId()))
                .andExpect(jsonPath("$.evidenceNumber").value(2));
        assertThat(evidences.findByRequestId(request.getId())).hasSize(2);
    }

    @Test
    void ownerCanCancelButCannotCancelTwice() throws Exception {
        mvc.perform(operation("cancel").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));
        mvc.perform(operation("cancel").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(Request.RequestStatus.CANCELADA);
    }

    @Test
    void ownerCannotBypassProposalSelectionWithDirectClose() throws Exception {
        mvc.perform(operation("close").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
        unchanged();
    }

    @Test
    void missingRequestReturns404ToClient() throws Exception {
        mvc.perform(get("/api/requests/9223372036854775807/evidences")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mvc.perform(operation("read").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountRoleChangeInvalidatesClientPermission() throws Exception {
        owner.setRole(Role.TECHNICIAN);
        users.saveAndFlush(owner);
        mvc.perform(operation("read").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
        unchanged();
    }

    @Test
    void validTokenForDeletedAccountIsRejected() throws Exception {
        users.deleteById(other.getId());
        users.flush();
        mvc.perform(operation("read").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isUnauthorized());
        unchanged();
    }

    @Test
    void serviceDoesNotTrustCallsWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> requestService.getEvidencesByRequestId(request.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        unchanged();
    }

    private User account(Role role) {
        return users.saveAndFlush(new User("Usuario de prueba", UUID.randomUUID()+"@example.com",
                passwords.encode("PermisosDemo2026!"), role));
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        String url = "/api/requests/" + request.getId();
        return switch (operation) {
            case "read" -> get(url + "/evidences");
            case "add" -> post(url + "/evidences").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mediaUrl\":\"https://example.com/new.jpg\",\"mediaType\":\"image/jpeg\"}");
            case "cancel" -> patch(url + "/cancel");
            case "close" -> patch(url + "/close");
            default -> throw new IllegalArgumentException(operation);
        };
    }

    private void unchanged() {
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(Request.RequestStatus.CON_PROPUESTAS);
        assertThat(evidences.findByRequestId(request.getId())).hasSize(1);
    }
}
