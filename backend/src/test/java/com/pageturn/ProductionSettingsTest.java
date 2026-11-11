package com.pageturn;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A real HTTP server and a real HTTP client with a cookie jar — the same steps a browser takes, with no
 * test shortcuts. Runs with production-like settings: real BCrypt cost, a low sign-in rate limit and an
 * admin account created from the environment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pageturn.security.jwt.secret=another-test-secret-that-is-long-enough-xyz",
        // The Java HTTP client won't send Secure cookies over plain http://localhost.
        "pageturn.security.cookie-secure=false",
        "pageturn.rate-limit.auth-per-minute=4",
        "pageturn.admin.email=Head.Librarian@Example.com",
        "pageturn.admin.password=a-very-long-admin-password",
        "pageturn.admin.name=Head Librarian",
        "server.address=127.0.0.1",
})
@Import(TestcontainersConfiguration.class)
// Runs the rate-limit test last: all requests come from 127.0.0.1 and share one sign-in budget.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductionSettingsTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

    @Test
    @Order(1)
    void browserFlowWithCsrfCookieSessionCookieAndSignOut() throws Exception {
        // 1. The app first fetches a CSRF token (arrives as a readable cookie).
        assertThat(send("GET", "/api/v1/auth/csrf", null, null).statusCode()).isEqualTo(204);
        String csrf = cookie("XSRF-TOKEN");
        assertThat(csrf).isNotBlank();

        // 2. Signing in without the token in the header is refused, even with the cookie present.
        String login = "{\"email\":\"head.librarian@example.com\",\"password\":\"a-very-long-admin-password\"}";
        assertThat(send("POST", "/api/v1/auth/login", login, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/auth/login", login, "wrong-token").statusCode()).isEqualTo(403);

        // 3. With the token: signed in; the session cookie is HttpOnly and SameSite=Strict.
        HttpResponse<String> ok = send("POST", "/api/v1/auth/login", login, csrf);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"role\":\"LIBRARIAN\"");
        String setCookie = ok.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("pageturn_session=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict");

        // 4. The librarian area works with the cookie; a change still needs the CSRF header.
        assertThat(send("GET", "/api/v1/admin/stats", null, null).statusCode()).isEqualTo(200);
        String book = "{\"title\":\"Browser test\",\"author\":\"A\",\"totalCopies\":1}";
        assertThat(send("POST", "/api/v1/admin/books", book, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/v1/admin/books", book, cookie("XSRF-TOKEN")).statusCode()).isEqualTo(201);

        // 5. Sign out: the cookie is removed and the old token no longer works.
        String oldSession = cookie("pageturn_session");
        assertThat(send("POST", "/api/v1/auth/logout", null, cookie("XSRF-TOKEN")).statusCode()).isEqualTo(204);
        assertThat(cookie("pageturn_session")).isNull();
        HttpResponse<String> replay = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/me"))
                .header("Cookie", "pageturn_session=" + oldSession).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(replay.statusCode()).isEqualTo(401);
    }

    @Test
    @Order(2)
    void firstLibrarianIsStoredWithAFullStrengthBcryptHash() {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'head.librarian@example.com' AND role = 'LIBRARIAN'",
                String.class);
        assertThat(hash).startsWith("{bcrypt}$2a$12$");
    }

    @Test
    @Order(3)
    void signInAttemptsFromOneAddressAreRateLimited() throws Exception {
        HttpClient fresh = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        int lastStatus = 0;
        String retryAfter = null;
        // Limit is 4 per minute and the browser-flow test already used 3 of them.
        for (int i = 0; i < 6; i++) {
            HttpResponse<String> response = fresh.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"x@example.com\",\"password\":\"whatever-pass\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            lastStatus = response.statusCode();
            retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        }
        assertThat(lastStatus).isEqualTo(429);
        assertThat(retryAfter).isNotBlank();

        // Writing the URL differently ("%61" = "a", "%6C" = "l") must not get around the limit.
        for (String disguised : new String[] {"/%61pi/v1/auth/login", "/api/v1/auth/%6Cogin", "/api//v1/auth/login"}) {
            HttpResponse<String> response = fresh.send(HttpRequest.newBuilder(uri(disguised))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"x@example.com\",\"password\":\"whatever-pass\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(disguised).isIn(400, 429);
        }
    }

    private HttpResponse<String> send(String method, String path, String json, String csrfHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        if (csrfHeader != null) {
            builder.header("X-XSRF-TOKEN", csrfHeader);
        }
        builder.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name) && !c.hasExpired())
                .map(HttpCookie::getValue).filter(v -> !v.isEmpty()).findFirst().orElse(null);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
