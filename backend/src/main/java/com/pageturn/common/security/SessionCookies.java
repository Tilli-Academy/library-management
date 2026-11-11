package com.pageturn.common.security;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

import com.pageturn.common.web.RequestPaths;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The login token lives in a cookie that JavaScript cannot read (HttpOnly). Even if an attacker managed
 * to run a script on the page (XSS), they could not steal the session.
 * <ul>
 * <li>HttpOnly – invisible to JavaScript.</li>
 * <li>Secure – only sent over HTTPS (configurable for plain-http local testing).</li>
 * <li>SameSite=Strict – never sent with requests started by other websites (CSRF defence, together with
 * the CSRF token checked by Spring Security).</li>
 * </ul>
 */
@Component
public class SessionCookies implements BearerTokenResolver {

    public static final String NAME = "pageturn_session";

    private final SecurityProperties properties;

    public SessionCookies(SecurityProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, properties.jwt().sessionTtl()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    /** Reads the token from the cookie (or null if there is none). */
    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Tells Spring Security where the token is. The public sign-in endpoints ignore the cookie, so an
     * old expired cookie can never block someone from signing in again.
     */
    @Override
    public String resolve(HttpServletRequest request) {
        String path = RequestPaths.of(request);
        if (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/me")) {
            return null;
        }
        return read(request);
    }
}
