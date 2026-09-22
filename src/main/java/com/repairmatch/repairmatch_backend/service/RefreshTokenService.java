package com.repairmatch.repairmatch_backend.service;

import com.repairmatch.repairmatch_backend.dto.LoginResponseDto;
import com.repairmatch.repairmatch_backend.exception.InvalidRefreshTokenException;
import com.repairmatch.repairmatch_backend.model.*;
import com.repairmatch.repairmatch_backend.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final JwtService jwtService;
    private final long lifetime;
    private final SecureRandom random = new SecureRandom();
    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService,
            @Value("${security.jwt.refresh-expiration-seconds:604800}") long lifetime) {
        if (lifetime <= 0) throw new IllegalArgumentException("Refresh lifetime must be positive");
        this.repository=repository; this.jwtService=jwtService; this.lifetime=lifetime;
    }

    @Transactional
    public LoginResponseDto startSession(User user) { return issue(user); }

    @Transactional
    public LoginResponseDto rotate(String rawToken) {
        RefreshToken previous = activeToken(rawToken);
        previous.setRevoked(true);
        return issue(previous.getUser());
    }

    @Transactional
    public void revoke(String rawToken) { activeToken(rawToken).setRevoked(true); }

    private RefreshToken activeToken(String rawToken) {
        RefreshToken token=repository.findForUpdate(hash(rawToken)).orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked() || !token.getExpiresAt().isAfter(Instant.now())) throw new InvalidRefreshTokenException();
        return token;
    }

    private LoginResponseDto issue(User user) {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken stored=new RefreshToken();
        stored.setTokenHash(hash(raw)); stored.setUser(user);
        stored.setExpiresAt(Instant.now().plusSeconds(lifetime)); repository.saveAndFlush(stored);
        LoginResponseDto access=jwtService.generateToken(user);
        return new LoginResponseDto(access.getAccessToken(),access.getTokenType(),access.getExpiresIn(),raw,lifetime);
    }

    public static String hash(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidRefreshTokenException();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable",ex); }
    }
}
