package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Test
    void registerLoginAndGetOwnProfile() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";

        // 1. Registrar una cuenta de prueba.
        String registrationResponse = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Usuario Prueba",
                                          "email": "%s",  
                                          "password": "PruebaLocal2026!",
                                          "role": "CLIENT"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userId = JsonPath.read(registrationResponse, "$.id");

        // 2. Iniciar sesión y obtener un token.
        String loginResponse = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "PruebaLocal2026!"
                                        }
                                        """.formatted(email))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(loginResponse, "$.accessToken");

        // 3. Consultar el perfil usando ese token.
        mockMvc.perform(
                        get("/api/users/me")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("Usuario Prueba"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test
    void rejectsProfileWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void rejectsProfileWithInvalidToken() throws Exception {
        mockMvc.perform(
                        get("/api/users/me")
                                .header("Authorization", "Bearer token-invalido")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test
    void rejectsLoginWithWrongPassword() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";

        // Crear una cuenta para esta prueba.
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Usuario Prueba",
                                      "email": "%s",
                                      "password": "PruebaLocal2026!",
                                      "role": "CLIENT"
                                    }
                                    """.formatted(email))
                )
                .andExpect(status().isCreated());

        // Intentar ingresar con una contraseña incorrecta.
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "%s",
                                      "password": "Incorrecta123!"
                                    }
                                    """.formatted(email))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
    @Test
    void rejectsLoginWithUnknownEmail() throws Exception {
        String email = "inexistente-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "email": "%s",
                                      "password": "PruebaLocal2026!"
                                    }
                                    """.formatted(email))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }
    @Test
    void rejectsExpiredToken() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";

        String response = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Usuario Prueba",
                                      "email": "%s",
                                      "password": "PruebaLocal2026!",
                                      "role": "CLIENT"
                                    }
                                    """.formatted(email))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userId = JsonPath.read(response, "$.id");
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        JwtClaimsSet validClaims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId)
                .issuedAt(now.minusSeconds(300))
                .expiresAt(now.plusSeconds(300))
                .claim("role", "CLIENT")
                .build();

        String validToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, validClaims)
        ).getTokenValue();

        // Confirmar que la cuenta y la firma permiten acceder.
        mockMvc.perform(
                        get("/api/users/me")
                                .header("Authorization", "Bearer " + validToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));

        // Cambiar únicamente el vencimiento y volver a firmar.
        JwtClaimsSet expiredClaims = JwtClaimsSet.from(validClaims)
                .expiresAt(now.minusSeconds(120))
                .build();

        String expiredToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, expiredClaims)
        ).getTokenValue();

        mockMvc.perform(
                        get("/api/users/me")
                                .header("Authorization", "Bearer " + expiredToken)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test
    void eachTokenReturnsOnlyItsOwnProfile() throws Exception {
        String[] userIds = new String[2];
        String[] tokens = new String[2];
        String[] emails = {
                "usuario-a-" + UUID.randomUUID() + "@example.com",
                "usuario-b-" + UUID.randomUUID() + "@example.com"
        };

        // Crear dos cuentas y obtener sus tokens.
        for (int i = 0; i < emails.length; i++) {
            String registration = mockMvc.perform(
                            post("/api/auth/register")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                        {
                                          "name": "Usuario Prueba",
                                          "email": "%s",
                                          "password": "PruebaLocal2026!",
                                          "role": "CLIENT"
                                        }
                                        """.formatted(emails[i]))
                    )
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            userIds[i] = JsonPath.read(registration, "$.id");

            String login = mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                        {
                                          "email": "%s",
                                          "password": "PruebaLocal2026!"
                                        }
                                        """.formatted(emails[i]))
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            tokens[i] = JsonPath.read(login, "$.accessToken");
        }

        // Cada token debe devolver exclusivamente su propia cuenta.
        for (int i = 0; i < emails.length; i++) {
            mockMvc.perform(
                            get("/api/users/me")
                                    .header("Authorization", "Bearer " + tokens[i])
                                    .param("userId", userIds[1 - i])
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userIds[i]))
                    .andExpect(jsonPath("$.email").value(emails[i]))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
    }


}