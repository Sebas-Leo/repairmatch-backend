package com.repairmatch.repairmatch_backend;

import org.springframework.boot.SpringApplication;

/** HTTP test fixture: uses the H2 configuration in src/test/resources. */
public class RegistrationApiTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepairmatchBackendApplication.class, args);
    }
}
