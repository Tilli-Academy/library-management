package com.pageturn;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Attacks the API the way an attacker would, and checks every attempt fails.
 */
class SecurityApiTest extends ApiTestSupport {

    @Test
    void anonymousUsersGet401EverywhereExceptThePublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/loans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void membersCannotUseAnyLibrarianEndpoint() throws Exception {
        TestUser librarian = librarian("lib");
        TestUser member = member("member");
        Long bookId = createBook(librarian, "Protected", 1);
        String book = """
                {"title":"Hacked","author":"Eve","totalCopies":1}""";

        mockMvc.perform(as(member, get("/api/v1/admin/stats"))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/admin/users"))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/admin/loans"))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON).content(book))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON).content(book))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, delete("/api/v1/admin/books/" + bookId))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", bookId, member.email())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(member, patch("/api/v1/admin/users/" + member.id())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LIBRARIAN\"}"))
                .andExpect(status().isForbidden());

        // Nothing changed.
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.title").value("Protected"));
        mockMvc.perform(as(member, get("/api/v1/auth/me"))).andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void changesWithoutTheCsrfTokenAreRefused() throws Exception {
        TestUser librarian = librarian("csrf");
        mockMvc.perform(post("/api/v1/admin/books").cookie(librarian.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"author\":\"Y\",\"totalCopies\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("security token")));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        TestUser user = member("headers");
        mockMvc.perform(as(user, get("/api/v1/books")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void unknownAndInternalEndpointsAreNotReachable() throws Exception {
        TestUser librarian = librarian("internal");
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/h2-console",
                "/api/v1/../actuator/env", "/swagger-ui.html", "/.env", "/.git/config"}) {
            mockMvc.perform(as(librarian, get(path))).andExpect(result -> {
                int code = result.getResponse().getStatus();
                if (code != 400 && code != 401 && code != 403 && code != 404) {
                    throw new AssertionError(path + " returned " + code);
                }
            });
        }
    }

    @Test
    void corsOnlyAllowsTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/books")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mockMvc.perform(options("/api/v1/books")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5177")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5177"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void oversizedRequestsAreRejectedBeforeParsing() throws Exception {
        String huge = "{\"email\":\"a@example.com\",\"password\":\"" + "x".repeat(70_000) + "\"}";
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void scriptsInTextAreStoredAsPlainTextAndReturnedAsJsonOnly() throws Exception {
        TestUser librarian = librarian("xss");
        String payload = "<script>alert(1)</script><img src=x onerror=alert(2)>";
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"Mallory","totalCopies":1}""", payload)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")))
                // Returned exactly as typed (React will show it as text, never run it).
                .andExpect(jsonPath("$.title").value(payload));
    }

    @Test
    void controlCharactersAreRejected() throws Exception {
        TestUser librarian = librarian("ctrl");
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad\\u0000Title","author":"A\\u001b[31mB","totalCopies":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title contains invalid characters."))
                .andExpect(jsonPath("$.errors.author").value("Author contains invalid characters."));
    }

    @Test
    void sqlInjectionInSearchIsTreatedAsPlainText() throws Exception {
        TestUser librarian = librarian("sqli");
        createBook(librarian, "Normal Book " + System.nanoTime(), 1);
        mockMvc.perform(as(librarian, get("/api/v1/books")).param("q", "' OR '1'='1' --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(as(librarian, get("/api/v1/books")).param("q", "%%%' OR 1=1; DROP TABLE books; --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void errorsNeverLeakInternalDetails() throws Exception {
        TestUser user = member("errors");
        mockMvc.perform(as(user, post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
        mockMvc.perform(as(user, get("/api/v1/books/not-a-number")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
