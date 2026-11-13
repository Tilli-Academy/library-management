package com.pageturn.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings, bound from {@code pageturn.security.*}. The app refuses to start with a weak JWT secret.
 */
@ConfigurationProperties(prefix = "pageturn.security")
public record SecurityProperties(Jwt jwt, boolean cookieSecure, int bcryptStrength) {

    static final int MIN_SECRET_BYTES = 32;

    public SecurityProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null);
        }
        if (bcryptStrength == 0) {
            bcryptStrength = 12;
        }
        if (bcryptStrength < 4 || bcryptStrength > 16) {
            throw new IllegalStateException("pageturn.security.bcrypt-strength must be between 4 and 16.");
        }
    }

    public record Jwt(String secret, Duration sessionTtl, String issuer) {

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("PAGETURN_JWT_SECRET must be set to a random value of at least "
                        + MIN_SECRET_BYTES + " characters. Generate one with: openssl rand -hex 48");
            }
            if (sessionTtl == null) {
                sessionTtl = Duration.ofHours(8);
            }
            if (issuer == null || issuer.isBlank()) {
                issuer = "pageturn";
            }
        }
    }
}
