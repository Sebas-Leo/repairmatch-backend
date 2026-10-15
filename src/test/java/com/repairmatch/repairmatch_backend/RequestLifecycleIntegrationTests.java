package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Request state machine as documented in docs/API_CONTRACT.md. EXPIRADA is reserved: nothing in the API sets it, so
 * these tests only prove that it behaves as a final state when present.
 */
class RequestLifecycleIntegrationTests extends BackendTestSupport {

    @Test
    void ownerCancelsAnOpenRequestFromPublishedAndFromWithProposals() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request published = request(owner, type);
        Request withProposals = request(owner, type);
        submit(withProposals, tech);

        for (Request request : new Request[] {published, withProposals}) {
            mvc.perform(patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", bearer(owner)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));
        }
    }

    @Test
    void finalStatesCannotBeCancelledOrChanged() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        for (Request.RequestStatus finalState : new Request.RequestStatus[] {
                Request.RequestStatus.CERRADA, Request.RequestStatus.CANCELADA, Request.RequestStatus.EXPIRADA}) {
            Request request = request(owner, type);
            request.setStatus(finalState);
            requests.save(request);

            mvc.perform(patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", bearer(owner)))
                    .andExpect(status().isConflict());
            mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":1.0,\"longitude\":2.0}"))
                    .andExpect(status().isConflict());
            assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(finalState);
        }
    }

    @Test
    void cancelRequiresTheOwningClient() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        Request request = request(owner, type);

        mvc.perform(patch("/api/requests/{id}/cancel", request.getId())).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", bearer(technician(type, LAT, LON))))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/requests/{id}/cancel", 987654L).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.PUBLICADA);
    }

    @Test
    void locationCanChangeOnlyWhilePublishedAndMustBeValid() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        Request request = request(owner, type);

        mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":-12.1,\"longitude\":-77.1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(-12.1)).andExpect(jsonPath("$.longitude").value(-77.1));
        mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":91,\"longitude\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.latitude").isNotEmpty());
        mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.longitude").isNotEmpty());
        mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(user(Role.CLIENT)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":1,\"longitude\":1}"))
                .andExpect(status().isForbidden());

        request.setLatitude(-12.1);
        request.setLongitude(-77.1);
        requests.save(request);
        submit(request, technician(type, -12.1, -77.1)); // proposals arrive -> CON_PROPUESTAS
        mvc.perform(patch("/api/requests/{id}/location", request.getId()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":1,\"longitude\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void locationMustBeSuppliedAsAPairWhenCreating() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();

        mvc.perform(post("/api/requests").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applianceTypeId\":" + type.getId() + ",\"originalDescription\":\"x\",\"latitude\":-12.0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isNotEmpty());
        mvc.perform(post("/api/requests").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applianceTypeId\":" + type.getId() + ",\"originalDescription\":\"x\",\"latitude\":-12.0,\"longitude\":-77.0}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.latitude").value(-12.0)).andExpect(jsonPath("$.status").value("PUBLICADA"));
        mvc.perform(post("/api/requests").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applianceTypeId\":987654,\"originalDescription\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCancelledOrExpiredRequestCannotBeSelected() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);

        Request cancelled = request(owner, type);
        long cancelledProposal = submit(cancelled, tech);
        mvc.perform(patch("/api/requests/{id}/cancel", cancelled.getId()).header("Authorization", bearer(owner))).andExpect(status().isOk());

        Request expired = request(owner, type);
        long expiredProposal = submit(expired, tech);
        expired = requests.findById(expired.getId()).orElseThrow();
        expired.setStatus(Request.RequestStatus.EXPIRADA);
        requests.save(expired);

        for (long proposalId : new long[] {cancelledProposal, expiredProposal}) {
            mvc.perform(post("/api/proposals/{id}/accept", proposalId).header("Authorization", bearer(owner)))
                    .andExpect(status().isConflict());
            assertThat(proposals.findById(proposalId).orElseThrow().getStatus()).isEqualTo(Proposal.Status.PENDING);
        }
        assertThat(services.count()).isZero();
    }

    @Test
    void anAcceptedRequestIsClosedForCancellationAndDirectClose() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);
        accept(submit(request, tech), owner);

        mvc.perform(patch("/api/requests/{id}/cancel", request.getId()).header("Authorization", bearer(owner))).andExpect(status().isConflict());
        mvc.perform(patch("/api/requests/{id}/close", request.getId()).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(Request.RequestStatus.CERRADA);
    }
}
