package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Service lifecycle, reviews, reputation and the service lookups a UI needs. */
class ServiceReviewIntegrationTests extends BackendTestSupport {

    private static final String REVIEW = "{\"rating\":5,\"comment\":\"Fixed\"}";

    // ---- review creation: response body (changed: used to be an empty 201) ----

    @Test
    void reviewReturnsTheCreatedReviewWithoutReviewerIdentity() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        long serviceId = completedService(request(owner, type), owner, tech);

        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"comment\":\"  Fixed it  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.serviceId").value(serviceId))
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Fixed it"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")))
                .andExpect(jsonPath("$.clientId").doesNotExist());
        assertThat(reviews.count()).isEqualTo(1);
    }

    @Test
    void reviewValidationRejectsBadPayloadsWithFieldErrors() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        long serviceId = completedService(request(owner, type), owner, technician(type, LAT, LON));

        for (String body : new String[] {"{\"rating\":0,\"comment\":\"x\"}", "{\"rating\":6,\"comment\":\"x\"}"}) {
            mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.rating").isNotEmpty());
        }
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.rating").isNotEmpty());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.comment").isNotEmpty());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.comment").isNotEmpty());
        assertThat(reviews.count()).isZero();
    }

    @Test
    void reviewRejectsMissingAuthWrongRoleNonOwnerUnknownServiceAndBadState() throws Exception {
        User owner = user(Role.CLIENT);
        User outsider = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);
        long serviceId = accept(submit(request, tech), owner);

        mvc.perform(post("/api/services/{id}/review", serviceId).contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isUnauthorized());
        // not completed yet
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isConflict());
        advance(serviceId, tech, "EN_ATENCION");
        advance(serviceId, tech, "COMPLETADO");

        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(tech))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/services/{id}/review", 987654L).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isNotFound());
        assertThat(reviews.count()).isZero();

        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(REVIEW))
                .andExpect(status().isConflict());
        assertThat(reviews.count()).isEqualTo(1);
    }

    // ---- GET /services/{id}/review ----

    @Test
    void reviewCanBeReadByBothParticipantsOnly() throws Exception {
        User owner = user(Role.CLIENT);
        User outsider = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        User otherTech = technician(type, LAT, LON);
        long serviceId = completedService(request(owner, type), owner, tech);

        mvc.perform(get("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW)).andExpect(status().isCreated());

        for (User participant : new User[] {owner, tech}) {
            mvc.perform(get("/api/services/{id}/review", serviceId).header("Authorization", bearer(participant)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.serviceId").value(serviceId))
                    .andExpect(jsonPath("$.rating").value(5)).andExpect(jsonPath("$.comment").value("Fixed"));
        }
        for (User stranger : new User[] {outsider, otherTech}) {
            mvc.perform(get("/api/services/{id}/review", serviceId).header("Authorization", bearer(stranger)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/services/{id}/review", serviceId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/services/{id}/review", 987654L).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/services/abc/review").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /technicians/{id}/reviews and reputation ----

    @Test
    void technicianReviewsAndReputationOnlyCountThatTechniciansJobs() throws Exception {
        User client = user(Role.CLIENT);
        ApplianceType type = type();
        User techA = technician(type, LAT, LON);
        User techB = technician(type, LAT, LON);
        User techC = technician(type, LAT, LON);

        long a1 = completedService(request(client, type), client, techA);
        long a2 = completedService(request(client, type), client, techA);
        long b1 = completedService(request(client, type), client, techB);
        for (long[] review : new long[][] {{a1, 5}, {a2, 3}, {b1, 1}}) {
            mvc.perform(post("/api/services/{id}/review", review[0]).header("Authorization", bearer(client))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\":" + review[1] + ",\"comment\":\"Rated " + review[1] + "\"}")).andExpect(status().isCreated());
        }

        // newest first, readable by any authenticated account (here: another technician)
        mvc.perform(get("/api/technicians/{id}/reviews", techA.getId()).header("Authorization", bearer(techC)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].serviceId").value(a2)).andExpect(jsonPath("$[0].rating").value(3))
                .andExpect(jsonPath("$[1].serviceId").value(a1)).andExpect(jsonPath("$[1].rating").value(5))
                .andExpect(jsonPath("$[0].clientId").doesNotExist());
        mvc.perform(get("/api/technicians/{id}/reviews", techB.getId()).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].rating").value(1));
        mvc.perform(get("/api/technicians/{id}/reviews", techC.getId()).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/technicians/{id}/reputation", techA.getId()).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(4.0)).andExpect(jsonPath("$.reviewCount").value(2));
        mvc.perform(get("/api/technicians/{id}/reputation", techB.getId()).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(1.0)).andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(get("/api/technicians/{id}/reputation", techC.getId()).header("Authorization", bearer(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.averageRating").value(0.0)).andExpect(jsonPath("$.reviewCount").value(0));
    }

    @Test
    void technicianReviewsRejectAnonymousUnknownAndMalformedIds() throws Exception {
        User client = user(Role.CLIENT);
        mvc.perform(get("/api/technicians/{id}/reviews", java.util.UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/technicians/{id}/reviews", java.util.UUID.randomUUID()).header("Authorization", bearer(client)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/technicians/not-a-uuid/reviews").header("Authorization", bearer(client)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /requests/{id}/service ----

    @Test
    void ownerFindsTheServiceOfARequestOnlyAfterAcceptance() throws Exception {
        User owner = user(Role.CLIENT);
        User outsider = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        Request request = request(owner, type);

        mvc.perform(get("/api/requests/{id}/service", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        long serviceId = accept(submit(request, tech), owner);

        mvc.perform(get("/api/requests/{id}/service", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(serviceId))
                .andExpect(jsonPath("$.requestId").value(request.getId()))
                .andExpect(jsonPath("$.status").value("PROGRAMADO"));
        mvc.perform(get("/api/requests/{id}/service", request.getId()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{id}/service", request.getId()).header("Authorization", bearer(tech)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{id}/service", request.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/{id}/service", 987654L).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/abc/service").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /services: scoped to the caller, with request/technician references ----

    @Test
    void serviceListIsScopedToTheCallerAndLinksRequestAndTechnician() throws Exception {
        User owner = user(Role.CLIENT);
        User otherClient = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        User otherTech = technician(type, LAT, LON);
        Request request = request(owner, type);
        long serviceId = accept(submit(request, tech), owner);
        long otherServiceId = accept(submit(request(otherClient, type), otherTech), otherClient);

        for (User participant : new User[] {owner, tech}) {
            mvc.perform(get("/api/services").header("Authorization", bearer(participant)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(serviceId))
                    .andExpect(jsonPath("$[0].requestId").value(request.getId()))
                    .andExpect(jsonPath("$[0].clientId").value(owner.getId().toString()))
                    .andExpect(jsonPath("$[0].technicianId").value(tech.getId().toString()))
                    .andExpect(jsonPath("$[0].technicianName").value(tech.getName()))
                    .andExpect(jsonPath("$[0].status").value("PROGRAMADO"));
        }
        mvc.perform(get("/api/services/{id}", otherServiceId).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/services").header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
    }

    // ---- service state machine ----

    @Test
    void invalidServiceTransitionsAreRejectedAndKeepTheStatus() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        long serviceId = accept(submit(request(owner, type), tech), owner);

        // PROGRAMADO cannot jump to COMPLETADO or back to PROGRAMADO
        patchStatus(serviceId, tech, "COMPLETADO", 409);
        patchStatus(serviceId, tech, "PROGRAMADO", 409);
        // the client may only cancel
        patchStatus(serviceId, owner, "EN_ATENCION", 403);
        patchStatus(serviceId, owner, "COMPLETADO", 403);
        advance(serviceId, tech, "EN_ATENCION");
        // started work can no longer be cancelled, nor restarted
        patchStatus(serviceId, owner, "CANCELADO", 409);
        patchStatus(serviceId, tech, "CANCELADO", 409);
        patchStatus(serviceId, tech, "EN_ATENCION", 409);
        advance(serviceId, tech, "COMPLETADO");
        // final state is never reopened
        patchStatus(serviceId, tech, "EN_ATENCION", 409);
        patchStatus(serviceId, tech, "COMPLETADO", 409);
        patchStatus(serviceId, tech, "CANCELADO", 409);
        assertThat(services.findById(serviceId).orElseThrow().getStatus()).isEqualTo(ServiceEntity.ServiceStatus.COMPLETADO);
    }

    @Test
    void eitherParticipantCancelsAScheduledServiceAndItStaysCancelled() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        long byClient = accept(submit(request(owner, type), tech), owner);
        long byTechnician = accept(submit(request(owner, type), tech), owner);

        patchStatus(byClient, owner, "CANCELADO", 200);
        patchStatus(byTechnician, tech, "CANCELADO", 200);
        patchStatus(byClient, tech, "EN_ATENCION", 409);
        patchStatus(byClient, owner, "CANCELADO", 409);
        assertThat(services.findById(byClient).orElseThrow().getStatus()).isEqualTo(ServiceEntity.ServiceStatus.CANCELADO);
        // a cancelled service cannot be reviewed
        mvc.perform(post("/api/services/{id}/review", byClient).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(REVIEW)).andExpect(status().isConflict());
    }

    @Test
    void nonParticipantsAndAnonymousCannotChangeOrReadAService() throws Exception {
        User owner = user(Role.CLIENT);
        User outsider = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        User otherTech = technician(type, LAT, LON);
        long serviceId = accept(submit(request(owner, type), tech), owner);

        for (User stranger : new User[] {outsider, otherTech}) {
            patchStatus(serviceId, stranger, "EN_ATENCION", 403);
            patchStatus(serviceId, stranger, "CANCELADO", 403);
            mvc.perform(get("/api/services/{id}", serviceId).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/services/{id}/status", serviceId).param("status", "CANCELADO")).andExpect(status().isUnauthorized());
        patchStatus(987654L, tech, "EN_ATENCION", 404);
        assertThat(services.findById(serviceId).orElseThrow().getStatus()).isEqualTo(ServiceEntity.ServiceStatus.PROGRAMADO);
    }

    private void patchStatus(long serviceId, User actor, String status, int expected) throws Exception {
        mvc.perform(patch("/api/services/{id}/status", serviceId).param("status", status).header("Authorization", bearer(actor)))
                .andExpect(status().is(expected));
    }
}
