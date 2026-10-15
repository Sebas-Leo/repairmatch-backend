package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.service.CompatibilityPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** What a technician UI needs: own profile, own proposals, request detail for a bid, and compatible opportunities. */
class TechnicianFlowIntegrationTests extends BackendTestSupport {

    // ---- GET /technicians/me/profile ----

    @Test
    void ownProfileIsEmptyUntilSavedThenReturnsPrivateFields() throws Exception {
        User tech = user(Role.TECHNICIAN);
        ApplianceType type = type();

        mvc.perform(get("/api/technicians/me/profile").header("Authorization", bearer(tech)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tech.getId().toString()))
                .andExpect(jsonPath("$.fullName").value(tech.getName()))
                .andExpect(jsonPath("$.email").value(tech.getEmail()))
                .andExpect(jsonPath("$.applianceTypes", hasSize(0)))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.maxRadiusKm").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(technicians.count()).isZero(); // a GET never creates the profile

        mvc.perform(put("/api/technicians/me/profile").header("Authorization", bearer(tech)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"experienceYears\":7,\"bio\":\"Fridges\",\"phone\":\"999111222\",\"applianceTypeIds\":[" + type.getId() + "]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/technicians/me/service-areas").header("Authorization", bearer(tech)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"latitude\":-12.0464,\"longitude\":-77.0428,\"maxRadiusKm\":15}")).andExpect(status().isOk());

        mvc.perform(get("/api/technicians/me/profile").header("Authorization", bearer(tech)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceYears").value(7))
                .andExpect(jsonPath("$.bio").value("Fridges"))
                .andExpect(jsonPath("$.phone").value("999111222"))
                .andExpect(jsonPath("$.email").value(tech.getEmail()))
                .andExpect(jsonPath("$.latitude").value(-12.0464))
                .andExpect(jsonPath("$.longitude").value(-77.0428))
                .andExpect(jsonPath("$.maxRadiusKm").value(15.0))
                .andExpect(jsonPath("$.applianceTypes[0]").value(type.getName()));

        // the public view of the same technician still hides the private fields
        mvc.perform(get("/api/technicians/{id}", tech.getId()).header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.latitude").doesNotExist());
    }

    @Test
    void ownProfileRejectsAnonymousAndClients() throws Exception {
        mvc.perform(get("/api/technicians/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/technicians/me/profile").header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isForbidden());
    }

    // ---- GET /technicians/me/proposals ----

    @Test
    void ownProposalsListsOnlyMineNewestFirstWithStatus() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User tech = technician(type, LAT, LON);
        User rival = technician(type, LAT, LON);
        Request first = request(owner, type);
        Request second = request(owner, type);

        long firstProposal = submit(first, tech);
        long secondProposal = submit(second, tech);
        long rivalProposal = submit(first, rival);
        accept(rivalProposal, owner);

        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(tech)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(secondProposal)).andExpect(jsonPath("$[0].requestId").value(second.getId()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].technicianId").value(tech.getId().toString()))
                .andExpect(jsonPath("$[0].diagnosticCost").value(45.5))
                .andExpect(jsonPath("$[1].id").value(firstProposal)).andExpect(jsonPath("$[1].status").value("REJECTED"));
        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(rival)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(rivalProposal)).andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(technician(type, LAT, LON))))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownProposalsRejectAnonymousAndClients() throws Exception {
        mvc.perform(get("/api/technicians/me/proposals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/technicians/me/proposals").header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isForbidden());
    }

    // ---- GET /requests/{id} as a technician ----

    @Test
    void technicianReadsTheRequestOfItsProposalButOnlyTheSelectedOneSeesTheLocation() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        User chosen = technician(type, LAT, LON);
        User rejected = technician(type, LAT, LON);
        User bystander = technician(type, LAT, LON);
        Request request = request(owner, type);

        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(chosen)))
                .andExpect(status().isForbidden()); // no proposal yet
        long chosenProposal = submit(request, chosen);
        submit(request, rejected);

        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(chosen)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(request.getId()))
                .andExpect(jsonPath("$.status").value("CON_PROPUESTAS"))
                .andExpect(jsonPath("$.originalDescription").value("Does not start"))
                .andExpect(jsonPath("$.applianceTypeName").value(type.getName()))
                .andExpect(jsonPath("$.latitude").doesNotExist()).andExpect(jsonPath("$.longitude").doesNotExist());

        accept(chosenProposal, owner);
        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(chosen)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CERRADA"))
                .andExpect(jsonPath("$.latitude").value(LAT)).andExpect(jsonPath("$.longitude").value(LON));
        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(rejected)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").doesNotExist());
        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(bystander)))
                .andExpect(status().isForbidden());
        // the owner always sees what was stored
        mvc.perform(get("/api/requests/{id}", request.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(LAT));
        mvc.perform(get("/api/requests/{id}", request.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/{id}", 987654L).header("Authorization", bearer(chosen))).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/abc").header("Authorization", bearer(chosen))).andExpect(status().isBadRequest());
    }

    // ---- GET /technicians/me/matching-requests ----

    @Test
    void matchingReturnsOnlyOpenCompatibleRequestsNearestFirstWithPaging() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType served = type();
        ApplianceType other = type();
        User tech = technician(served, LAT, LON);

        Request near = located(owner, served, LAT + 0.01, LON, Request.RequestStatus.PUBLICADA);
        Request farther = located(owner, served, LAT + 0.05, LON, Request.RequestStatus.CON_PROPUESTAS);
        located(owner, other, LAT + 0.01, LON, Request.RequestStatus.PUBLICADA);          // type not served
        located(owner, served, LAT + 1.0, LON, Request.RequestStatus.PUBLICADA);          // ~111 km, outside 20 km
        located(owner, served, LAT + 0.01, LON, Request.RequestStatus.CERRADA);           // not open
        located(owner, served, LAT + 0.01, LON, Request.RequestStatus.CANCELADA);
        located(owner, served, LAT + 0.01, LON, Request.RequestStatus.EXPIRADA);
        located(owner, served, null, null, Request.RequestStatus.PUBLICADA);              // no location

        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(tech)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].requestId").value(near.getId())).andExpect(jsonPath("$[0].distanceKm").value(1.11))
                .andExpect(jsonPath("$[0].status").value("PUBLICADA")).andExpect(jsonPath("$[0].latitude").doesNotExist())
                .andExpect(jsonPath("$[1].requestId").value(farther.getId())).andExpect(jsonPath("$[1].status").value("CON_PROPUESTAS"));
        mvc.perform(get("/api/technicians/me/matching-requests?page=0&size=1").header("Authorization", bearer(tech)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].requestId").value(near.getId()));
        mvc.perform(get("/api/technicians/me/matching-requests?page=1&size=1").header("Authorization", bearer(tech)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].requestId").value(farther.getId()));
        mvc.perform(get("/api/technicians/me/matching-requests?page=2&size=1").header("Authorization", bearer(tech)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void matchingRadiusIsInclusiveAtTheExactBoundary() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType type = type();
        double exact = CompatibilityPolicy.distanceKm(LAT, LON, LAT + 0.02, LON);
        User atBoundary = technician(type, LAT, LON, exact);
        User justShort = technician(type, LAT, LON, Math.nextDown(exact));
        Request request = located(owner, type, LAT + 0.02, LON, Request.RequestStatus.PUBLICADA);

        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(atBoundary)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].requestId").value(request.getId()));
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(justShort)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void matchingEdgeCases() throws Exception {
        User client = user(Role.CLIENT);
        ApplianceType type = type();
        located(client, type, LAT, LON, Request.RequestStatus.PUBLICADA);

        mvc.perform(get("/api/technicians/me/matching-requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(client))).andExpect(status().isForbidden());

        User noProfile = user(Role.TECHNICIAN);
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(noProfile)))
                .andExpect(status().isNotFound());

        User noArea = user(Role.TECHNICIAN);
        technicians.save(new Technician(noArea));
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(noArea)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());

        User noTypes = technician(type, LAT, LON);
        Technician profile = technicians.findByIdWithApplianceTypes(noTypes.getId()).orElseThrow();
        profile.setApplianceTypes(new java.util.HashSet<>());
        technicians.save(profile);
        mvc.perform(get("/api/technicians/me/matching-requests").header("Authorization", bearer(noTypes)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        User tech = technician(type, LAT, LON);
        for (String query : new String[] {"page=-1", "size=0", "size=101", "page=abc"}) {
            mvc.perform(get("/api/technicians/me/matching-requests?" + query).header("Authorization", bearer(tech)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    private Request located(User owner, ApplianceType type, Double latitude, Double longitude, Request.RequestStatus status) {
        return requests.save(Request.builder().client(owner).applianceType(type).originalDescription("Does not start")
                .latitude(latitude).longitude(longitude).status(status).build());
    }
}
