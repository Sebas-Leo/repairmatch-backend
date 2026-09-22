package com.repairmatch.repairmatch_backend;

import com.jayway.jsonpath.JsonPath;
import com.repairmatch.repairmatch_backend.exception.InvalidRefreshTokenException;
import com.repairmatch.repairmatch_backend.repository.RefreshTokenRepository;
import com.repairmatch.repairmatch_backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:refresh_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "security.cors.allowed-origins=http://localhost:3000"})
@AutoConfigureMockMvc
class RefreshIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired RefreshTokenRepository tokens;
    @Autowired RefreshTokenService service;
    @Autowired JwtDecoder decoder;

    @Test void loginIncludesIdentityClaimsAndStoresOnlyRefreshHash() throws Exception {
        String login=login();
        String raw=JsonPath.read(login,"$.refreshToken");
        var stored=tokens.findByTokenHash(RefreshTokenService.hash(raw)).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(raw);
        String access=JsonPath.read(login,"$.accessToken");
        var jwt=decoder.decode(access);
        assertThat(jwt.getClaimAsString("userId")).isEqualTo(jwt.getSubject());
        assertThat(jwt.getClaimAsString("email")).endsWith("@example.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CLIENT");
    }

    @Test void rotationInvalidatesPreviousTokenAndNewAccessWorks() throws Exception {
        String raw=JsonPath.read(login(),"$.refreshToken");
        String rotated=mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String next=JsonPath.read(rotated,"$.refreshToken");
        assertThat(next).isNotEqualTo(raw);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.timestamp").isNotEmpty());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+JsonPath.read(rotated,"$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test void logoutRevokesRefresh() throws Exception {
        String raw=JsonPath.read(login(),"$.refreshToken");
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test void expiredRefreshRejected() throws Exception {
        String raw=JsonPath.read(login(),"$.refreshToken");
        var stored=tokens.findByTokenHash(RefreshTokenService.hash(raw)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));tokens.saveAndFlush(stored);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body(raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test void unknownRefreshRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(body("invalid")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.path").value("/api/auth/refresh"));
    }

    @Test void refreshIsNotAnAccessToken() throws Exception {
        String raw=JsonPath.read(login(),"$.refreshToken");
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+raw))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.path").value("/api/users/me"));
    }

    @Test void concurrentRotationOnlySucceedsOnce() throws Exception {
        String raw=JsonPath.read(login(),"$.refreshToken");
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<Integer> attempt=()->{ready.countDown();start.await();try{service.rotate(raw);return 200;}
            catch(InvalidRefreshTokenException ex){return 401;}};
        try {
            Future<Integer> first=executor.submit(attempt),second=executor.submit(attempt);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,401);
        } finally {start.countDown();executor.shutdownNow();}
    }

    @Test void allowedCorsPreflightSucceeds() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin","http://localhost:3000")
                        .header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
    }

    @Test void unknownCorsOriginRejected() throws Exception {
        mvc.perform(options("/api/users/me").header("Origin","https://untrusted.example")
                        .header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void missingRefreshIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.refreshToken").isNotEmpty());
    }

    private String login() throws Exception {
        String email="refresh-"+UUID.randomUUID()+"@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"email\":\""+email+"\",\"password\":\"PruebaLocal2026!\",\"role\":\"CLIENT\"}"))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\""+email+"\",\"password\":\"PruebaLocal2026!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private String body(String token) {return "{\"refreshToken\":\""+token+"\"}";}
}
