package com.repairmatch.repairmatch_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} failed logins for one email within
 * {@link #WINDOW}, that email is refused until the window passes. In memory on purpose: the API
 * runs as a single instance, and a restart only forgives a few attempts.
 */
@Component
public class LoginAttemptLimiter {

    public static final int MAX_FAILURES = 10;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    /** Keeps the map bounded if someone sprays many different emails. */
    private static final int MAX_TRACKED = 10_000;

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email) {
        Attempts current = attempts.get(email);
        if (current == null) return false;
        if (expired(current)) {
            attempts.remove(email, current);
            return false;
        }
        return current.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String email) {
        if (attempts.size() >= MAX_TRACKED) attempts.values().removeIf(this::expired);
        attempts.compute(email, (key, current) -> current == null || expired(current)
                ? new Attempts(1, clock.instant())
                : new Attempts(current.failures() + 1, current.firstFailure()));
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }

    private boolean expired(Attempts value) {
        return !clock.instant().isBefore(value.firstFailure().plus(WINDOW));
    }
}
