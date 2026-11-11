package com.pageturn.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.pageturn.ApiTestSupport;

class UserAdminApiTest extends ApiTestSupport {

    @Test
    void librarianCanSearchUsersWithoutSeeingPasswordHashes() throws Exception {
        TestUser librarian = librarian("admin-list");
        TestUser member = member("findme");
        mockMvc.perform(as(librarian, get("/api/v1/admin/users")).param("q", member.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].email").value(member.email()))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    }

    @Test
    void promotionTakesEffectImmediately() throws Exception {
        TestUser librarian = librarian("promoter");
        TestUser member = member("promoted");
        mockMvc.perform(as(member, get("/api/v1/admin/stats"))).andExpect(status().isForbidden());

        mockMvc.perform(as(librarian, patch("/api/v1/admin/users/" + member.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LIBRARIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIBRARIAN"));

        // Same cookie as before — the role is read from the database, not from the token.
        mockMvc.perform(as(member, get("/api/v1/admin/stats"))).andExpect(status().isOk());
    }

    @Test
    void blockingEndsTheSessionAndUnblockingDoesNotRestoreOldCookies() throws Exception {
        TestUser librarian = librarian("blocker");
        TestUser member = member("blockee");
        mockMvc.perform(as(librarian, patch("/api/v1/admin/users/" + member.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(as(member, get("/api/v1/books"))).andExpect(status().isUnauthorized());

        mockMvc.perform(as(librarian, patch("/api/v1/admin/users/" + member.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(member, get("/api/v1/books"))).andExpect(status().isUnauthorized());
    }

    @Test
    void librariansCannotChangeThemselves() throws Exception {
        TestUser librarian = librarian("self");
        mockMvc.perform(as(librarian, patch("/api/v1/admin/users/" + librarian.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoLibrariansCannotDemoteEachOtherAtTheSameMoment() throws Exception {
        for (int round = 0; round < 5; round++) {
            TestUser a = librarian("duel-a");
            TestUser b = librarian("duel-b");
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var aDemotesB = pool.submit(() -> mockMvc.perform(as(a, patch("/api/v1/admin/users/" + b.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                        .andReturn().getResponse().getStatus());
                var bDemotesA = pool.submit(() -> mockMvc.perform(as(b, patch("/api/v1/admin/users/" + a.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                        .andReturn().getResponse().getStatus());
                int first = aDemotesB.get();
                int second = bDemotesA.get();
                // Exactly one wins; the loser is no longer a librarian by the time its change runs.
                assertThat(java.util.List.of(first, second)).containsExactlyInAnyOrder(200, 403);
            }
            finally {
                pool.shutdownNow();
            }
            Integer librarians = jdbc.queryForObject(
                    "SELECT count(*) FROM users WHERE id IN (?, ?) AND role = 'LIBRARIAN'", Integer.class, a.id(), b.id());
            assertThat(librarians).isEqualTo(1);
        }
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        TestUser librarian = librarian("badrole");
        TestUser member = member("target");
        mockMvc.perform(as(librarian, patch("/api/v1/admin/users/" + member.id()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }
}
