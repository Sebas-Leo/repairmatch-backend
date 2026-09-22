package com.repairmatch.repairmatch_backend;

import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;

/** Explicit test-only entry point; fixtures cannot be included in the production jar. */
public class PermissionApiTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{RepairmatchBackendApplication.class, Fixtures.class}, args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        CommandLineRunner permissionFixtures(UserRepository users, RequestRepository requests,
                ApplianceTypeRepository types, EvidenceRepository evidences,
                PasswordEncoder encoder, Environment environment) {
            return args -> {
                if (!environment.getRequiredProperty("spring.datasource.url").startsWith("jdbc:h2:mem:")) {
                    throw new IllegalStateException("Permission fixtures require an in-memory H2 database");
                }
                String hash = encoder.encode("PermisosDemo2026!");
                User owner = users.saveAndFlush(new User("Cliente propietario", "owner.permissions@example.com", hash, Role.CLIENT));
                users.saveAndFlush(new User("Otro cliente", "other.permissions@example.com", hash, Role.CLIENT));
                users.saveAndFlush(new User("Técnico", "technician.permissions@example.com", hash, Role.TECHNICIAN));
                ApplianceType type = types.saveAndFlush(ApplianceType.builder().name("Lavadora de prueba").build());
                Request request = requests.saveAndFlush(Request.builder().client(owner).applianceType(type)
                        .originalDescription("Solicitud de prueba de permisos")
                        .status(Request.RequestStatus.CON_PROPUESTAS).build());
                evidences.saveAndFlush(Evidence.builder().id(new EvidenceId(request.getId(), 1)).request(request)
                        .mediaUrl("https://example.com/private.jpg").mediaType("image/jpeg").build());
                Files.createDirectories(Path.of("target"));
                Files.writeString(Path.of("target/permissions-fixture.json"),
                        "{\"requestId\": " + request.getId() + "}");
            };
        }
    }
}
