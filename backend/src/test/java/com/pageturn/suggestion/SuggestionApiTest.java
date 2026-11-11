package com.pageturn.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.pageturn.ApiTestSupport;

class SuggestionApiTest extends ApiTestSupport {

    private long request(TestUser user, String title) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"Some Author","note":"Please!"}""", title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(true))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void fiveLikesPutTheBookInTheCatalogueAsComingSoon() throws Exception {
        String title = "Popular Request " + System.nanoTime();
        TestUser asker = member("asker");
        long id = request(asker, title);

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(as(member("fan" + i), post("/api/v1/suggestions/" + id + "/like")))
                    .andExpect(jsonPath("$.likeCount").value(1 + i))
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }
        String body = mockMvc.perform(as(member("fan4"), post("/api/v1/suggestions/" + id + "/like")))
                .andExpect(jsonPath("$.likeCount").value(5))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.bookStatus").value("COMING_SOON"))
                .andReturn().getResponse().getContentAsString();
        Number bookId = JsonPath.read(body, "$.bookId");

        mockMvc.perform(as(asker, get("/api/v1/books/" + bookId)))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value("COMING_SOON"))
                .andExpect(jsonPath("$.totalCopies").value(0));
        mockMvc.perform(as(asker, get("/api/v1/books")).param("q", title).param("comingSoon", "true"))
                .andExpect(jsonPath("$.totalItems").value(1));
        // Approved requests can't be liked any more.
        mockMvc.perform(as(member("late-fan"), post("/api/v1/suggestions/" + id + "/like"))).andExpect(status().isConflict());
    }

    @Test
    void eachMemberLikesOnceAndCanTakeTheLikeBack() throws Exception {
        TestUser asker = member("asker2");
        TestUser fan = member("fan-twice");
        long id = request(asker, "Like Once " + System.nanoTime());
        mockMvc.perform(as(fan, post("/api/v1/suggestions/" + id + "/like"))).andExpect(jsonPath("$.likeCount").value(2));
        mockMvc.perform(as(fan, post("/api/v1/suggestions/" + id + "/like"))).andExpect(jsonPath("$.likeCount").value(2));
        mockMvc.perform(as(fan, delete("/api/v1/suggestions/" + id + "/like")))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByMe").value(false));
        mockMvc.perform(as(fan, delete("/api/v1/suggestions/" + id + "/like"))).andExpect(jsonPath("$.likeCount").value(1));
    }

    @Test
    void likesArrivingAtTheSameMomentAreAllCountedAndTheBookIsAddedOnce() throws Exception {
        String title = "Rush Request " + System.nanoTime();
        long id = request(member("rush-asker"), title);
        List<TestUser> fans = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fans.add(member("rush" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(fans.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (TestUser fan : fans) {
                results.add(pool.submit(() -> mockMvc.perform(as(fan, post("/api/v1/suggestions/" + id + "/like")))
                        .andReturn().getResponse().getStatus()));
            }
            int ok = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                assertThat(code).isIn(200, 409); // 409 = arrived after the request was already approved
                ok += code == 200 ? 1 : 0;
            }
            assertThat(ok).isEqualTo(4); // 1 (asker) + 4 likes = 5 = approved; later likes are refused
        }
        finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT like_count FROM book_suggestions WHERE id = ?", Integer.class, id)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books WHERE title = ?", Integer.class, title)).isEqualTo(1);
    }

    @Test
    void duplicatesAndBooksAlreadyInTheCatalogueAreRefused() throws Exception {
        TestUser librarian = librarian("dup-lib");
        TestUser member = member("dup-member");
        String title = "Asked Twice " + System.nanoTime();
        request(member, title);
        mockMvc.perform(as(member("other"), post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"SOME AUTHOR"}""", title.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Someone already requested this book. Find it in the list and like it instead."));

        String owned = "Already Owned " + System.nanoTime();
        createBook(librarian, owned, 1);
        mockMvc.perform(as(member, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"Test Author"}""", owned)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This book is already in the catalogue."));
    }

    @Test
    void brandNewAccountsCannotRequestOrLikeSoFakeAccountsCannotStockBooks() throws Exception {
        TestUser established = member("old-timer");
        long id = request(established, "Needs Real Fans " + System.nanoTime());
        TestUser fresh = member("fresh");
        jdbc.update("UPDATE users SET created_at = now() WHERE id = ?", fresh.id()); // just signed up
        mockMvc.perform(as(fresh, post("/api/v1/suggestions/" + id + "/like")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("New accounts can request and like books after 24 hours")));
        mockMvc.perform(as(fresh, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spam Book\",\"author\":\"Spammer\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT like_count FROM book_suggestions WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void approvedRequestsDoNotCopyTheMembersNoteIntoTheCatalogue() throws Exception {
        TestUser librarian = librarian("note-check");
        TestUser member = member("note-writer");
        long id = request(member, "Note Book " + System.nanoTime()); // note is "Please!"
        String body = mockMvc.perform(as(librarian, post("/api/v1/admin/suggestions/" + id + "/approve")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number bookId = JsonPath.read(body, "$.bookId");
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void invisibleCharactersAndExtraSpacesCannotSneakPastDuplicateChecks() throws Exception {
        String title = "Sneaky Title " + System.nanoTime();
        request(member("sneak-a"), title);
        TestUser other = member("sneak-b");
        for (String disguised : new String[] {title.replace(" ", "\u200b ") , "  " + title.replace(" ", "\u00a0\u00a0") + "\u202e"}) {
            mockMvc.perform(as(other, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                            .content(json("""
                                    {"title":"%s","author":"Some Author"}""", disguised)))
                    .andExpect(status().isConflict());
        }
        // A title made only of invisible characters is refused clearly (not a database error).
        mockMvc.perform(as(other, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\u200b\u200b\",\"author\":\"Some Author\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Title is required."));
    }

    @Test
    void membersCanHaveAtMostFiveOpenRequests() throws Exception {
        TestUser member = member("eager");
        for (int i = 0; i < 5; i++) {
            request(member, "Eager " + i + " " + System.nanoTime());
        }
        mockMvc.perform(as(member, post("/api/v1/suggestions")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"One too many\",\"author\":\"X\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyLibrariansApproveOrDeclineAndOnlyTheySeeWhoAsked() throws Exception {
        TestUser librarian = librarian("decider");
        TestUser member = member("hopeful");
        long declined = request(member, "Declined " + System.nanoTime());
        long approved = request(member, "Approved By Hand " + System.nanoTime());

        mockMvc.perform(as(member, post("/api/v1/admin/suggestions/" + approved + "/approve"))).andExpect(status().isForbidden());
        mockMvc.perform(as(member, get("/api/v1/suggestions")))
                .andExpect(jsonPath("$.items[?(@.id == " + approved + ")].requestedByName").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
        mockMvc.perform(as(librarian, get("/api/v1/suggestions")))
                .andExpect(jsonPath("$.items[?(@.id == " + approved + ")].requestedByName").value(org.hamcrest.Matchers.hasItem("hopeful")));

        mockMvc.perform(as(librarian, post("/api/v1/admin/suggestions/" + approved + "/approve")))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.bookStatus").value("COMING_SOON"));
        mockMvc.perform(as(librarian, post("/api/v1/admin/suggestions/" + declined + "/decline")))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.bookId").doesNotExist());
        mockMvc.perform(as(member, post("/api/v1/suggestions/" + declined + "/like"))).andExpect(status().isConflict());
        mockMvc.perform(as(member, get("/api/v1/suggestions")).param("status", "DECLINED"))
                .andExpect(jsonPath("$.items[?(@.id == " + declined + ")]").exists());
    }

    @Test
    void comingSoonBooksCannotBeLentUntilCopiesArrive() throws Exception {
        TestUser librarian = librarian("arrivals");
        TestUser member = member("waiting");
        String body = mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Next Month's Hit","author":"New Author","totalCopies":0,"comingSoon":true,"expectedArrival":"2026-12-15"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMING_SOON"))
                .andExpect(jsonPath("$.expectedArrival").value("2026-12-15"))
                .andReturn().getResponse().getContentAsString();
        Number bookId = JsonPath.read(body, "$.id");

        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", bookId.longValue(), member.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This book isn't in stock yet (coming soon)."));

        // Copies arrived: now in stock and lendable.
        mockMvc.perform(as(librarian, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/books/" + bookId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Next Month's Hit","author":"New Author","totalCopies":3,"comingSoon":false}"""))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableCopies").value(3))
                .andExpect(jsonPath("$.expectedArrival").doesNotExist());
        issue(librarian, bookId.longValue(), member);

        // A book with copies on loan can't go back to "coming soon".
        mockMvc.perform(as(librarian, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/books/" + bookId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Next Month's Hit","author":"New Author","totalCopies":0,"comingSoon":true}"""))
                .andExpect(status().isConflict());
    }
}
