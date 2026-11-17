package com.repairmatch.repairmatch_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.repairmatch.repairmatch_backend.service.LoginAttemptLimiter;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-16T12:00:00Z");

        void advance(Duration by) { now = now.plus(by); }

        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private static LoginAttemptLimiter limiter(Clock clock) throws Exception {
        Constructor<LoginAttemptLimiter> ctor = LoginAttemptLimiter.class.getDeclaredConstructor(Clock.class);
        ctor.setAccessible(true);
        return ctor.newInstance(clock);
    }

    @Test
    void blocksAnEmailAfterTooManyFailuresUntilTheWindowPasses() throws Exception {
        MovableClock clock = new MovableClock();
        LoginAttemptLimiter limiter = limiter(clock);
        for (int i = 1; i < LoginAttemptLimiter.MAX_FAILURES; i++) limiter.recordFailure("a@x.pe");
        assertThat(limiter.isBlocked("a@x.pe")).isFalse();

        limiter.recordFailure("a@x.pe");
        assertThat(limiter.isBlocked("a@x.pe")).isTrue();
        assertThat(limiter.isBlocked("b@x.pe")).as("other accounts are unaffected").isFalse();

        clock.advance(LoginAttemptLimiter.WINDOW);
        assertThat(limiter.isBlocked("a@x.pe")).isFalse();
    }

    @Test
    void aSuccessfulLoginClearsTheFailures() throws Exception {
        LoginAttemptLimiter limiter = limiter(new MovableClock());
        for (int i = 1; i < LoginAttemptLimiter.MAX_FAILURES; i++) limiter.recordFailure("a@x.pe");
        limiter.recordSuccess("a@x.pe");
        limiter.recordFailure("a@x.pe");
        assertThat(limiter.isBlocked("a@x.pe")).isFalse();
    }
}
