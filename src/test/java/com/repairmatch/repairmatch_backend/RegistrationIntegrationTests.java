package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
import com.repairmatch.repairmatch_backend.model.Role;
import com.repairmatch.repairmatch_backend.model.User;
import com.repairmatch.repairmatch_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TECHNICIAN"})
    void registersAccountWithEncodedPassword(String role) throws Exception {
        String email = uniqueEmail();
        String password = "PruebaLocal2026!";

        String response = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationJson(
                                        "Usuario Prueba", email, password, role
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Usuario Prueba"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        String responseId = JsonPath.read(response, "$.id");

        assertThat(savedUser.getId().toString()).isEqualTo(responseId);
        assertThat(savedUser.getName()).isEqualTo("Usuario Prueba");
        assertThat(savedUser.getRole()).isEqualTo(Role.valueOf(role));
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(
                password, savedUser.getPasswordHash()
        )).isTrue();
    }

    @Test
    void normalizesEmailAndTrimsName() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationJson(
                                        "  Usuario Prueba  ",
                                        email.toUpperCase(Locale.ROOT),
                                        "PruebaLocal2026!",
                                        "CLIENT"
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Usuario Prueba"))
                .andExpect(jsonPath("$.email").value(email));

        User savedUser = userRepository.findByEmail(email).orElseThrow();

        assertThat(savedUser.getName()).isEqualTo("Usuario Prueba");
        assertThat(savedUser.getEmail()).isEqualTo(email);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsDuplicateEmailWithoutChangingAccount(
            boolean uppercase
    ) throws Exception {
        String email = uniqueEmail();
        String originalPassword = "PruebaLocal2026!";

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationJson(
                                        "Usuario Original",
                                        email,
                                        originalPassword,
                                        "CLIENT"
                                ))
                )
                .andExpect(status().isCreated());

        User original = userRepository.findByEmail(email).orElseThrow();
        UUID originalId = original.getId();
        String originalHash = original.getPasswordHash();
        long countBefore = userRepository.count();

        String duplicateEmail = uppercase
                ? email.toUpperCase(Locale.ROOT)
                : email;

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationJson(
                                        "Otro Usuario",
                                        duplicateEmail,
                                        "OtraPassword2026!",
                                        "TECHNICIAN"
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(countBefore);

        User unchanged = userRepository.findByEmail(email).orElseThrow();

        assertThat(unchanged.getId()).isEqualTo(originalId);
        assertThat(unchanged.getName()).isEqualTo("Usuario Original");
        assertThat(unchanged.getRole()).isEqualTo(Role.CLIENT);
        assertThat(unchanged.getPasswordHash()).isEqualTo(originalHash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankName(String name) throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                name, uniqueEmail(), "PruebaLocal2026!", "CLIENT"
        ));
    }

    @Test
    void rejectsNameLongerThan100Characters() throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                "a".repeat(101),
                uniqueEmail(),
                "PruebaLocal2026!",
                "CLIENT"
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "incorrecto", "usuario@"})
    void rejectsInvalidEmail(String email) throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba", email, "PruebaLocal2026!", "CLIENT"
        ));
    }

    @Test
    void rejectsEmailLongerThan254Characters() throws Exception {
        String email = "a".repeat(64) + "@"
                + "b".repeat(63) + "."
                + "c".repeat(63) + "."
                + "d".repeat(63) + ".com";

        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba", email, "PruebaLocal2026!", "CLIENT"
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "        ", "123", "1234567"})
    void rejectsBlankOrShortPassword(String password) throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba", uniqueEmail(), password, "CLIENT"
        ));
    }

    @Test
    void rejectsPasswordLongerThan72Characters() throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba",
                uniqueEmail(),
                "a".repeat(73),
                "CLIENT"
        ));
    }

    @Test
    void rejectsPasswordLongerThan72Utf8Bytes() throws Exception {
        // 37 caracteres, pero 74 bytes en UTF-8.
        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba",
                uniqueEmail(),
                "á".repeat(37),
                "CLIENT"
        ));
    }

    @Test
    void acceptsPasswordOfExactly72Utf8Bytes() throws Exception {
        String email = uniqueEmail();
        String password = "á".repeat(36);

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationJson(
                                        "Usuario Prueba",
                                        email,
                                        password,
                                        "CLIENT"
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User savedUser = userRepository.findByEmail(email).orElseThrow();

        assertThat(passwordEncoder.matches(
                password, savedUser.getPasswordHash()
        )).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DESCONOCIDO"})
    void rejectsUnknownRole(String role) throws Exception {
        assertBadRequestWithoutSaving(registrationJson(
                "Usuario Prueba",
                uniqueEmail(),
                "PruebaLocal2026!",
                role
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "password", "role"})
    void rejectsMissingRequiredField(String field) throws Exception {
        var document = JsonPath.parse(registrationJson(
                "Usuario Prueba",
                uniqueEmail(),
                "PruebaLocal2026!",
                "CLIENT"
        ));

        document.delete("$." + field);

        assertBadRequestWithoutSaving(document.jsonString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "password", "role"})
    void rejectsNullRequiredField(String field) throws Exception {
        var document = JsonPath.parse(registrationJson(
                "Usuario Prueba",
                uniqueEmail(),
                "PruebaLocal2026!",
                "CLIENT"
        ));

        document.set("$." + field, null);

        assertBadRequestWithoutSaving(document.jsonString());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertBadRequestWithoutSaving("{\"name\":");
    }

    @Test
    void rejectsMissingBody() throws Exception {
        long countBefore = userRepository.count();

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "-1", "2", "true", "[]", "{}"})
    void rejectsNonStringRole(String roleJson) throws Exception {
        String body = registrationJson("Usuario Prueba", uniqueEmail(),
                "PruebaLocal2026!", "CLIENT");
        assertBadRequestWithoutSaving(body.replace("\"CLIENT\"", roleJson));
    }

    private void assertBadRequestWithoutSaving(String body) throws Exception {
        long countBefore = userRepository.count();

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    private String uniqueEmail() {
        return "registro-" + UUID.randomUUID() + "@example.com";
    }

    private String registrationJson(
            String name,
            String email,
            String password,
            String role
    ) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(name, email, password, role);
    }
}