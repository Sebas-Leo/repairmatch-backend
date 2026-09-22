package com.repairmatch.repairmatch_backend.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import java.util.*;
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String origins) {
        var allowed=Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (allowed.contains("*")) throw new IllegalArgumentException("Configure explicit CORS origins");
        CorsConfiguration configuration=new CorsConfiguration();
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));
        configuration.setExposedHeaders(List.of("WWW-Authenticate"));
        configuration.setAllowCredentials(false); configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**",configuration); return source;
    }
}
