package com.pageturn.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

    @Test
    void allowsUpToTheLimitPerWindowThenAsksToWaitThenResets() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T10:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        RateLimitFilter.FixedWindowLimiter limiter = new RateLimitFilter.FixedWindowLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        now.set(now.get().plusSeconds(15));
        assertThat(limiter.tryAcquire("1.2.3.4")).isEqualTo(45);
        assertThat(limiter.tryAcquire("5.6.7.8")).isZero(); // other clients are unaffected

        now.set(now.get().plusSeconds(45));
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero(); // new window
    }
}
