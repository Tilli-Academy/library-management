package com.pageturn.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.pageturn.common.security.ProblemJsonSecurityHandler;
import com.pageturn.common.security.SecurityProperties;
import com.pageturn.common.security.SessionCookies;
import com.pageturn.common.security.UserJwtAuthenticationConverter;

import jakarta.servlet.DispatcherType;

/**
 * Who may call what.
 *
 * <p><b>Deny by default:</b> only the few endpoints marked {@code permitAll} are public. Everything else
 * needs a signed-in user, and everything under {@code /api/v1/admin/} needs the LIBRARIAN role (checked
 * here AND again on each admin controller with {@code @PreAuthorize}, as a second lock on the door).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionCookies sessionCookies, JwtDecoder jwtDecoder,
            UserJwtAuthenticationConverter userConverter, SecurityProperties properties) throws Exception {
        ProblemJsonSecurityHandler problemHandler = new ProblemJsonSecurityHandler();

        // CSRF token for single-page apps: readable cookie XSRF-TOKEN, sent back in the X-XSRF-TOKEN header.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.secure(properties.cookieSecure()).sameSite("Strict"));

        http
            .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfRepository))
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.disable())
            .headers(headers -> headers
                // The API only returns JSON: it may never load scripts, be framed or be sniffed as HTML.
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
            .authorizeHttpRequests(auth -> auth
                // Let Spring render 400/404 errors for public endpoints instead of hiding them behind 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                        "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("LIBRARIAN")
                .requestMatchers("/api/**").authenticated()
                // Anything else (unknown paths, /actuator/env, ...) is refused outright.
                .anyRequest().denyAll())
            // Session-cookie authentication. Deliberately NOT http.oauth2ResourceServer(): that helper turns CSRF
            // protection off for every request carrying a token, which is right for Authorization headers but
            // wrong for cookies (browsers attach cookies automatically, which is exactly what CSRF abuses).
            .addFilter(sessionCookieFilter(jwtDecoder, sessionCookies, userConverter, problemHandler))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(problemHandler)
                .accessDeniedHandler(problemHandler));
        return http.build();
    }

    private static BearerTokenAuthenticationFilter sessionCookieFilter(JwtDecoder jwtDecoder,
            SessionCookies sessionCookies, UserJwtAuthenticationConverter userConverter,
            ProblemJsonSecurityHandler problemHandler) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(userConverter);
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setBearerTokenResolver(sessionCookies);
        filter.setAuthenticationEntryPoint(problemHandler);
        return filter;
    }

    /**
     * Only used when the browser calls the API from a different origin (e.g. a separately hosted frontend).
     * Exact origins only — never "*" — and credentials (cookies) allowed only for those origins.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
