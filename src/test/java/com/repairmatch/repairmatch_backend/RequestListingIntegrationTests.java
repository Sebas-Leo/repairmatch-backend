package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/requests: pagination envelope, status filter, text search and sort. */
class RequestListingIntegrationTests extends BackendTestSupport {

    private Request request(User owner, ApplianceType type, String description, String brand, Request.RequestStatus status) {
        return requests.save(Request.builder().client(owner).applianceType(type)
                .originalDescription(description).brand(brand).model("X1").status(status)
                .latitude(LAT).longitude(LON).build());
    }

    @Test
    void pagesOwnRequestsNewestFirstWithMetadata() throws Exception {
        User owner = user(Role.CLIENT);
        User other = user(Role.CLIENT);
        ApplianceType type = type();
        Request first = request(owner, type);
        Request second = request(owner, type);
        Request third = request(owner, type);
        request(other, type);

        mvc.perform(get("/api/requests?page=0&size=2").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()))
                .andExpect(jsonPath("$.content[1].id").value(second.getId()))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false));

        mvc.perform(get("/api/requests?page=1&size=2").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()))
                .andExpect(jsonPath("$.last").value(true));

        mvc.perform(get("/api/requests?sort=oldest&size=1").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.content[0].id").value(first.getId()));

        mvc.perform(get("/api/requests?page=5").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtersByStatusAndSearchesDescriptionBrandAndType() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType fridge = types.save(ApplianceType.builder().name("Refrigerador").description("Test").build());
        ApplianceType washer = types.save(ApplianceType.builder().name("Lavadora").description("Test").build());
        Request noisy = request(owner, fridge, "Hace mucho RUIDO al enfriar", "Samsung", Request.RequestStatus.PUBLICADA);
        Request cancelled = request(owner, washer, "No centrifuga", "LG", Request.RequestStatus.CANCELADA);
        request(owner, washer, "Pierde agua", "Mabe", Request.RequestStatus.PUBLICADA);

        mvc.perform(get("/api/requests?status=CANCELADA").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelled.getId()));

        mvc.perform(get("/api/requests?search=ruido").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(noisy.getId()));

        mvc.perform(get("/api/requests?search=samsung").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.content[0].id").value(noisy.getId()));

        mvc.perform(get("/api/requests?search=lavadora&status=PUBLICADA").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].brand").value("Mabe"));

        mvc.perform(get("/api/requests").param("search", "100%").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/requests").param("search", "   ").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsInvalidListingParameters() throws Exception {
        User owner = user(Role.CLIENT);
        for (String query : new String[] {"page=-1", "size=0", "size=101", "page=abc", "page=2147483647", "status=ROTA", "sort=price"}) {
            mvc.perform(get("/api/requests?" + query).header("Authorization", bearer(owner)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
    }
}
