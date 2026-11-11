package com.pageturn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pageturn.common.config.CorsProperties;
import com.pageturn.common.persistence.LikePatterns;
import com.pageturn.common.security.SecurityProperties;

/** Fast unit tests (no database): the app must refuse unsafe configuration. */
class ConfigurationSafetyTest {

    @Test
    void missingOrShortJwtSecretStopsTheApp() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(null, null, null))
                .hasMessageContaining("PAGETURN_JWT_SECRET");
        assertThatThrownBy(() -> new SecurityProperties.Jwt("too-short", null, null))
                .hasMessageContaining("at least 32");
    }

    @Test
    void wildcardCorsOriginsAreRefused() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*"))).isInstanceOf(IllegalStateException.class);
        assertThat(new CorsProperties(List.of(" https://a.example ", "")).allowedOrigins())
                .containsExactly("https://a.example");
    }

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(LikePatterns.contains(" 50%_Off\\ ")).isEqualTo("%50\\%\\_off\\\\%");
        assertThat(LikePatterns.contains("  ")).isEqualTo("%");
    }
}
