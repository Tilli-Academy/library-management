package com.pageturn;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.pageturn.common.security.SessionCookies;

import jakarta.servlet.http.Cookie;

/**
 * Base class for API tests: real Spring context, real PostgreSQL (Testcontainers), MockMvc as the browser.
 * Every test creates its own users and books, so tests never depend on each other.
 */
@SpringBootTest(properties = {
        "pageturn.security.jwt.secret=test-secret-that-is-long-enough-0123456789abcdef", // gitleaks:allow (fake key, tests only)
        "pageturn.security.bcrypt-strength=4",
        "pageturn.rate-limit.auth-per-minute=100000",
        "pageturn.rate-limit.api-per-minute=100000",
        "pageturn.admin.email=",
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** A signed-up user and their session cookie. */
    protected record TestUser(UUID id, String email, Cookie session) {
    }

    protected static String uniqueEmail(String prefix) {
        return (prefix + "-" + UUID.randomUUID() + "@example.com").toLowerCase(Locale.ROOT);
    }

    protected TestUser member(String name) throws Exception {
        String email = uniqueEmail(name);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","displayName":"%s","phone":"+91 98765 43210"}""", email, PASSWORD, name)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return new TestUser(UUID.fromString(id), email, result.getResponse().getCookie(SessionCookies.NAME));
    }

    protected TestUser librarian(String name) throws Exception {
        TestUser user = member(name);
        jdbc.update("UPDATE users SET role = 'LIBRARIAN' WHERE id = ?", user.id());
        return user;
    }

    /** Sends the request as this user (session cookie + CSRF token, like the real frontend). */
    protected static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.cookie(user.session()).with(csrf());
    }

    protected Long createBook(TestUser librarian, String title, int copies) throws Exception {
        String body = mockMvc.perform(as(librarian, post("/api/v1/admin/books"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"Test Author","totalCopies":%d}""", title, copies)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected Long issue(TestUser librarian, Long bookId, TestUser member) throws Exception {
        String body = mockMvc.perform(as(librarian, post("/api/v1/admin/loans"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", bookId, member.email())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static String json(String template, Object... args) {
        return template.formatted(args);
    }
}
