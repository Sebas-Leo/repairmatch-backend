package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Who may bid, on what, and with which payload; plus the compatibility rejection reasons. */
class ProposalRulesIntegrationTests extends BackendTestSupport {

    @Test
    void compatibleTechnicianBidsAndTheRequestMovesToProposals() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);

        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(request.getId()))
                .andExpect(jsonPath("$.technicianId").value(tech.getId().toString()))
                .andExpect(jsonPath("$.technicianName").value(tech.getName()))
                .andExpect(jsonPath("$.diagnosticCost").value(45.5))
                .andExpect(jsonPath("$.conditions").value("Diagnostic visit"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CON_PROPUESTAS);
    }

    @Test
    void duplicateBidFromTheSameTechnicianIsAConflict() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);
        submit(request, tech);

        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(proposals.count()).isEqualTo(1);
    }

    @Test
    void invalidPayloadsAreRejectedWithFieldErrors() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);
        String future = LocalDateTime.now().plusDays(1).toString();

        assertBadRequest(request, tech, "{\"diagnosticCost\":-1,\"availableAt\":\"" + future + "\",\"conditions\":\"ok\"}", "diagnosticCost");
        assertBadRequest(request, tech, "{\"diagnosticCost\":10.123,\"availableAt\":\"" + future + "\",\"conditions\":\"ok\"}", "diagnosticCost");
        assertBadRequest(request, tech, "{\"availableAt\":\"" + future + "\",\"conditions\":\"ok\"}", "diagnosticCost");
        assertBadRequest(request, tech, "{\"diagnosticCost\":10,\"availableAt\":\"" + LocalDateTime.now().minusDays(1) + "\",\"conditions\":\"ok\"}", "availableAt");
        assertBadRequest(request, tech, "{\"diagnosticCost\":10,\"conditions\":\"ok\"}", "availableAt");
        assertBadRequest(request, tech, "{\"diagnosticCost\":10,\"availableAt\":\"" + future + "\",\"conditions\":\"  \"}", "conditions");
        assertBadRequest(request, tech, "{\"diagnosticCost\":10,\"availableAt\":\"" + future + "\",\"conditions\":\"" + "x".repeat(1001) + "\"}", "conditions");
        assertThat(proposals.count()).isZero();
    }

    @Test
    void zeroCostIsAllowed() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        mvc.perform(post("/api/requests/{id}/proposals", request(owner, type).getId()).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosticCost\":0,\"availableAt\":\"" + LocalDateTime.now().plusDays(1) + "\",\"conditions\":\"Free visit\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.diagnosticCost").value(0.0));
    }

    @Test
    void anonymousClientAndUnknownRequestAreRejected() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);

        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/requests/{id}/proposals", 987654L).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isNotFound());
        assertThat(proposals.count()).isZero();
    }

    @Test
    void incompatibleTechniciansAreRejectedWithTheReason() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        Request request = request(owner, type);

        User wrongType = technician(type(), LAT, LON);
        User tooFar = technician(type, LAT + 1.0, LON);
        User noArea = user(Role.TECHNICIAN);
        Technician unconfigured = new Technician(noArea);
        unconfigured.setApplianceTypes(java.util.Set.of(type));
        technicians.save(unconfigured);
        Request noLocation = requests.save(Request.builder().client(owner).applianceType(type).originalDescription("No location").build());
        User fine = technician(type, LAT, LON);

        assertForbidden(request, wrongType, "appliance type");
        assertForbidden(request, tooFar, "service radius");
        assertForbidden(request, noArea, "not configured");
        assertForbidden(noLocation, fine, "no location");
        assertThat(proposals.count()).isZero();
    }

    @Test
    void technicianWithoutProfileGetsABadRequest() throws Exception {
        User owner = user(Role.CLIENT);
        mvc.perform(post("/api/requests/{id}/proposals", request(owner, type()).getId()).header("Authorization", bearer(user(Role.TECHNICIAN)))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void closedRequestsNoLongerAcceptBids() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        for (Request.RequestStatus closed : new Request.RequestStatus[] {
                Request.RequestStatus.CERRADA, Request.RequestStatus.CANCELADA, Request.RequestStatus.EXPIRADA}) {
            Request request = request(owner, type);
            request.setStatus(closed);
            requests.save(request);
            mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                            .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                    .andExpect(status().isConflict());
        }
        assertThat(proposals.count()).isZero();
    }

    @Test
    void ownerComparesProposalsOrderedByCostThenAvailabilityAndOthersCannot() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User cheap = technician(type, LAT, LON);
        User pricey = technician(type, LAT, LON);
        Request request = request(owner, type);
        bid(request, pricey, 90, 1);
        bid(request, cheap, 30, 2);

        mvc.perform(get("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].technicianId").value(cheap.getId().toString())).andExpect(jsonPath("$[0].diagnosticCost").value(30.0))
                .andExpect(jsonPath("$[1].technicianId").value(pricey.getId().toString()));
        mvc.perform(get("/api/requests/{id}/proposals", request.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(cheap)))
                .andExpect(status().isForbidden());
    }

    private void bid(Request request, User tech, int cost, int days) throws Exception {
        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosticCost\":" + cost + ",\"availableAt\":\"" + LocalDateTime.now().plusDays(days) + "\",\"conditions\":\"c\"}"))
                .andExpect(status().isCreated());
    }

    private void assertBadRequest(Request request, User tech, String body, String field) throws Exception {
        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).isNotEmpty());
    }

    private void assertForbidden(Request request, User tech, String reason) throws Exception {
        mvc.perform(post("/api/requests/{id}/proposals", request.getId()).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(proposalBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(containsString(reason)));
    }
}
