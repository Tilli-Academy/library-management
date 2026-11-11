package com.pageturn.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins that may call the API directly (bound from {@code pageturn.cors.*}).
 * A wildcard is refused, because the API uses cookies.
 */
@ConfigurationProperties(prefix = "pageturn.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
        for (String origin : allowedOrigins) {
            if (origin.contains("*")) {
                throw new IllegalStateException("PAGETURN_CORS_ALLOWED_ORIGINS must list exact origins, not '" + origin + "'.");
            }
        }
    }
}
