package com.pageturn.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pageturn.ApiTestSupport;
import com.pageturn.common.security.SessionCookies;

import jakarta.servlet.http.Cookie;

class AuthApiTest extends ApiTestSupport {

    @Test
    void signUpCreatesAMemberAndSetsASecureHttpOnlyCookie() throws Exception {
        String email = uniqueEmail("ada");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","displayName":"Ada","phone":"+91 98765 43210"}""", email.toUpperCase(), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(SessionCookies.NAME + "=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
    }

    @Test
    void signUpCannotChooseItsOwnRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","displayName":"Eve","phone":"0123456789","role":"LIBRARIAN","enabled":true}""",
                                uniqueEmail("eve"), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void passwordsAreStoredAsBcryptHashesNeverAsText() throws Exception {
        TestUser user = member("hash");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.id());
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void duplicateEmailsAreRefusedIgnoringCase() throws Exception {
        TestUser user = member("dup");
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","displayName":"Again","phone":"0123456789"}""", user.email().toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void weakOrInvalidSignUpDataGetsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":"Bad\\u0000Name","phone":"call me maybe"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").value("Password must be 12 to 72 characters."))
                .andExpect(jsonPath("$.errors.displayName").value("Name contains invalid characters."))
                .andExpect(jsonPath("$.errors.phone").value("Enter a valid phone number, e.g. +91 98765 43210."));
    }

    @Test
    void passwordsLongerThanBcryptsLimitAreRefused() throws Exception {
        String longPassword = "é".repeat(30) + "abcdefghijklm"; // 43 characters but 73 bytes
        mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s","displayName":"Long","phone":"0123456789"}""", uniqueEmail("long"), longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("at most 72 bytes")));
    }

    @Test
    void signInWorksAndFailuresDoNotRevealWhetherTheEmailExists() throws Exception {
        TestUser user = member("login");
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}""", user.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));

        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"wrong-password-123"}""", user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"wrong-password-123"}""", uniqueEmail("nobody"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void repeatedFailedSignInsAreBlockedEvenWithTheRightPassword() throws Exception {
        TestUser user = member("brute");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json("""
                                    {"email":"%s","password":"guess-number-%d"}""", user.email(), i)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}""", user.email(), PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void guessesSentAtTheSameMomentAreAllCounted() throws Exception {
        TestUser user = member("parallel");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int n = i;
                results.add(pool.submit(() -> mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("""
                                        {"email":"%s","password":"parallel-guess-%d"}""", user.email(), n)))
                        .andReturn().getResponse().getStatus()));
            }
            int checked = 0;
            for (java.util.concurrent.Future<Integer> result : results) {
                int code = result.get();
                assertThat(code).isIn(401, 429);
                checked += code == 401 ? 1 : 0;
            }
            // At most 5 passwords were actually tried; the rest were refused before checking.
            assertThat(checked).isLessThanOrEqualTo(5);
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void membersCanUpdateOnlyTheirOwnPhoneNumber() throws Exception {
        TestUser user = member("phone");
        mockMvc.perform(as(user, patch("/api/v1/auth/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+44 20 7946 0958\",\"role\":\"LIBRARIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+44 20 7946 0958"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mockMvc.perform(as(user, patch("/api/v1/auth/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"<script>\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/auth/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"0123456789\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        TestUser user = member("me");
        mockMvc.perform(get("/api/v1/auth/me").cookie(user.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void signOutEndsTheSessionEvenIfTheOldCookieWasCopied() throws Exception {
        TestUser user = member("logout");
        MvcResult result = mockMvc.perform(as(user, post("/api/v1/auth/logout")))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        mockMvc.perform(get("/api/v1/auth/me").cookie(user.session())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/books").cookie(user.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void anOldOrBrokenCookieNeverBlocksSigningInAgain() throws Exception {
        TestUser user = member("stale");
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).cookie(new Cookie(SessionCookies.NAME, "garbage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}""", user.email(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void forgedTokensAreRejected() throws Exception {
        TestUser user = member("forged");
        // Signed with a secret the server doesn't know.
        NimbusJwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "attacker-secret-attacker-secret-attacker".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = attacker.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("pageturn").subject(user.id().toString())
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                        .claims(c -> c.putAll(Map.of("ver", 0))).build())).getTokenValue();
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionCookies.NAME, forged)))
                .andExpect(status().isUnauthorized());

        // Unsigned token ("alg":"none").
        String unsigned = user.session().getValue().replaceAll("\\.[^.]+$", ".");
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(SessionCookies.NAME, unsigned)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedAccountsLoseAccessImmediatelyAndCannotSignIn() throws Exception {
        TestUser user = member("blocked");
        mockMvc.perform(get("/api/v1/books").cookie(user.session())).andExpect(status().isOk());

        jdbc.update("UPDATE users SET enabled = FALSE WHERE id = ?", user.id());

        mockMvc.perform(get("/api/v1/books").cookie(user.session())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"email":"%s","password":"%s"}""", user.email(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
