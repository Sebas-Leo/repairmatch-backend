package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Recomendado" for the client's proposals and "Para ti" for the technician's opportunities. */
class RecommendationIntegrationTests extends BackendTestSupport {

    private Request request(User owner, ApplianceType type, String brand, String description) {
        return requests.save(Request.builder().client(owner).applianceType(type)
                .originalDescription(description).brand(brand).model("X1")
                .latitude(LAT).longitude(LON).build());
    }

    private void review(long serviceId, User owner, int rating) throws Exception {
        mvc.perform(post("/api/services/{id}/review", serviceId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":" + rating + ",\"comment\":\"Buen trabajo\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void ranksProposalsByReputationAndExplainsWhy() throws Exception {
        ApplianceType fridge = type();
        User owner = user(Role.CLIENT);
        User someoneElse = user(Role.CLIENT);
        User reviewed = technician(fridge, LAT, LON);
        User newcomer = technician(fridge, LAT, LON);
        review(completedService(request(someoneElse, fridge, "LG", "No enfría"), someoneElse, reviewed), someoneElse, 5);

        Request mine = request(owner, fridge, "Samsung", "Mi refri no enfría");
        submit(mine, newcomer);
        submit(mine, reviewed);

        mvc.perform(get("/api/requests/{id}/proposals/recommended", mine.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].proposal.technicianId").value(reviewed.getId().toString()))
                .andExpect(jsonPath("$[0].recommended").value(true))
                .andExpect(jsonPath("$[0].averageRating").value(5.0))
                .andExpect(jsonPath("$[0].reviewCount").value(1))
                .andExpect(jsonPath("$[0].reasons[*].code", hasItem("TOP_RATED")))
                .andExpect(jsonPath("$[0].factors[*].key", hasItem("reputation")))
                .andExpect(jsonPath("$[1].recommended").value(false))
                .andExpect(jsonPath("$[1].reasons[*].code", hasItem("NO_REVIEWS")));
    }

    @Test
    void onlyTheOwnerSeesTheRecommendedOrder() throws Exception {
        ApplianceType fridge = type();
        User owner = user(Role.CLIENT);
        User stranger = user(Role.CLIENT);
        Request mine = request(owner, fridge, "LG", "No enfría");

        mvc.perform(get("/api/requests/{id}/proposals/recommended", mine.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/requests/{id}/proposals/recommended", mine.getId()).header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{id}/proposals/recommended", 999_999).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void forYouLearnsFromTheTechniciansPastJobs() throws Exception {
        ApplianceType fridge = type();
        User owner = user(Role.CLIENT);
        User tech = technician(fridge, LAT, LON);
        completedService(request(owner, fridge, "Samsung", "La refri dejó de enfriar"), owner, tech);

        Request other = request(owner, fridge, "LG", "Tiene un ruido raro");
        Request similar = request(owner, fridge, "Samsung", "Mi refri ya no enfría");
        Request answered = request(owner, fridge, "Samsung", "No enfría");
        submit(answered, tech);

        mvc.perform(get("/api/technicians/me/recommended-requests").header("Authorization", bearer(tech)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].request.requestId").value(similar.getId()))
                .andExpect(jsonPath("$.content[0].reasons[*].code", hasItem("BRAND_EXPERIENCE")))
                .andExpect(jsonPath("$.content[1].request.requestId").value(other.getId()))
                .andExpect(jsonPath("$.content[*].request.requestId", not(hasItem(answered.getId().intValue()))));
    }

    @Test
    void forYouIsOnlyForTechnicians() throws Exception {
        mvc.perform(get("/api/technicians/me/recommended-requests").header("Authorization", bearer(user(Role.CLIENT))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/technicians/me/recommended-requests")).andExpect(status().isUnauthorized());
    }
}
