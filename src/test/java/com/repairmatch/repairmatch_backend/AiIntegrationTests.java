package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.ApplianceType;
import com.repairmatch.repairmatch_backend.model.Request;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /api/ai: the local model structures descriptions and searches only the caller's own requests. */
class AiIntegrationTests extends BackendTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private ApplianceType catalog(String name) {
        return types.save(ApplianceType.builder().name(name).description("Test").build());
    }

    private Request request(User owner, ApplianceType type, String description, Request.RequestStatus status) {
        return requests.save(Request.builder().client(owner).applianceType(type)
                .originalDescription(description).brand("Test").model("X1").status(status)
                .latitude(LAT).longitude(LON).build());
    }

    @Test
    void structuresAFreeDescriptionKeepingTheOriginalText() throws Exception {
        User client = user(Role.CLIENT);
        ApplianceType fridge = catalog("Refrigerator");
        catalog("Washing machine");
        String text = "Mi refri Samsung RT38K5930SL dejó de enfriar desde ayer";

        mvc.perform(post("/api/ai/structure-request").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + text + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliance.value").value("REFRIGERADORA"))
                .andExpect(jsonPath("$.appliance.confident").value(true))
                .andExpect(jsonPath("$.applianceTypeId").value(fridge.getId()))
                .andExpect(jsonPath("$.symptom.value").value("NO_ENFRIA"))
                .andExpect(jsonPath("$.symptom.label").value("No enfría"))
                .andExpect(jsonPath("$.brand").value("Samsung"))
                .andExpect(jsonPath("$.model").value("RT38K5930SL"))
                .andExpect(jsonPath("$.originalDescription").value(text))
                .andExpect(jsonPath("$.source").value("local-ml"));
    }

    @Test
    void leavesFieldsEmptyWhenItUnderstandsNothing() throws Exception {
        User client = user(Role.CLIENT);
        mvc.perform(post("/api/ai/structure-request").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"hola, buenas tardes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliance").doesNotExist())
                .andExpect(jsonPath("$.applianceTypeId").doesNotExist())
                .andExpect(jsonPath("$.brand").doesNotExist());
    }

    @Test
    void searchesOwnRequestsWithTheFiltersItUnderstood() throws Exception {
        User owner = user(Role.CLIENT);
        User other = user(Role.CLIENT);
        ApplianceType fridge = catalog("Refrigerator");
        ApplianceType washer = catalog("Washing machine");
        Request leaking = request(owner, fridge, "La refri gotea agua debajo de los cajones", Request.RequestStatus.PUBLICADA);
        request(owner, fridge, "No enfría nada", Request.RequestStatus.PUBLICADA);
        request(owner, washer, "La lavadora gotea por abajo", Request.RequestStatus.PUBLICADA);
        request(other, fridge, "Mi refri también gotea", Request.RequestStatus.PUBLICADA);

        mvc.perform(post("/api/ai/search").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"la refri que gotea desde la semana pasada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understood[?(@.key == 'appliance')].value").value("Refrigeradora"))
                .andExpect(jsonPath("$.understood[?(@.key == 'symptom')].value").value("Fuga de agua"))
                .andExpect(jsonPath("$.understood[?(@.key == 'since')].value").value("hace 14 días"))
                .andExpect(jsonPath("$.results.totalElements").value(1))
                .andExpect(jsonPath("$.results.content[0].id").value(leaking.getId()))
                .andExpect(jsonPath("$.source").value("local-ml"));
    }

    @Test
    void searchMatchesAccentsAndStatus() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType washer = catalog("Washing machine");
        Request cancelled = request(owner, washer, "No centrifuga, la ropa sale empapada", Request.RequestStatus.CANCELADA);
        request(owner, washer, "No centrifuga bien", Request.RequestStatus.PUBLICADA);

        mvc.perform(post("/api/ai/search").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"mis lavadoras canceladas\",\"size\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.content", hasSize(1)))
                .andExpect(jsonPath("$.results.content[0].id").value(cancelled.getId()))
                .andExpect(jsonPath("$.results.size").value(5));

        // Accent-insensitive: "enfria" finds "enfría".
        ApplianceType fridge = catalog("Refrigerator");
        Request warm = request(owner, fridge, "Ya no enfría", Request.RequestStatus.PUBLICADA);
        mvc.perform(post("/api/ai/search").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"refri que no enfria\"}"))
                .andExpect(jsonPath("$.results.content[0].id").value(warm.getId()));
    }

    @Test
    void returnsNothingWhenItUnderstandsNothingAndRespectsTheAge() throws Exception {
        User owner = user(Role.CLIENT);
        ApplianceType fridge = catalog("Refrigerator");
        Request recent = request(owner, fridge, "La refri gotea", Request.RequestStatus.PUBLICADA);
        Request old = request(owner, fridge, "La refri gotea hace tiempo", Request.RequestStatus.PUBLICADA);
        // createdAt is not updatable through JPA: age the row directly.
        jdbc.update("update requests set created_at = ? where id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(40)), old.getId());

        mvc.perform(post("/api/ai/search").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"de la que\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understood", hasSize(0)))
                .andExpect(jsonPath("$.results.totalElements").value(0));

        mvc.perform(post("/api/ai/search").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"la refri que gotea desde la semana pasada\"}"))
                .andExpect(jsonPath("$.results.totalElements").value(1))
                .andExpect(jsonPath("$.results.content[0].id").value(recent.getId()));
    }

    @Test
    void onlyClientsUseItAndInputIsValidated() throws Exception {
        User technician = user(Role.TECHNICIAN);
        User client = user(Role.CLIENT);
        mvc.perform(post("/api/ai/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"refri\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/search").header("Authorization", bearer(technician))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"refri\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/structure-request").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").isNotEmpty());
        mvc.perform(post("/api/ai/search").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"refri\",\"size\":500}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/search").header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
