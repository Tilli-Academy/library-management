package com.pageturn.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.pageturn.ApiTestSupport;

class LoanApiTest extends ApiTestSupport {

    @Test
    void issuingAndReturningUpdatesCopiesAndTheMembersList() throws Exception {
        TestUser librarian = librarian("desk");
        TestUser member = member("reader");
        Long bookId = createBook(librarian, "Dune", 2);

        Long loanId = issue(librarian, bookId, member);
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.availableCopies").value(1));
        mockMvc.perform(as(member, get("/api/v1/me/loans")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].bookTitle").value("Dune"))
                .andExpect(jsonPath("$.items[0].overdue").value(false));

        mockMvc.perform(as(librarian, post("/api/v1/admin/loans/" + loanId + "/return")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt").exists());
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.availableCopies").value(2));
        mockMvc.perform(as(member, get("/api/v1/me/loans"))).andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(as(member, get("/api/v1/me/loans")).param("returned", "true"))
                .andExpect(jsonPath("$.totalItems").value(1));

        // Returning twice is refused and doesn't create a phantom copy.
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans/" + loanId + "/return"))).andExpect(status().isConflict());
        mockMvc.perform(as(member, get("/api/v1/books/" + bookId))).andExpect(jsonPath("$.availableCopies").value(2));
    }

    @Test
    void membersOnlyEverSeeTheirOwnLoans() throws Exception {
        TestUser librarian = librarian("privacy");
        TestUser alice = member("alice");
        TestUser bob = member("bob");
        issue(librarian, createBook(librarian, "Alice's book", 1), alice);

        mockMvc.perform(as(bob, get("/api/v1/me/loans"))).andExpect(jsonPath("$.totalItems").value(0));
        // Extra parameters trying to pick another member are simply ignored.
        mockMvc.perform(as(bob, get("/api/v1/me/loans")).param("memberId", alice.id().toString()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void lendingRulesAreEnforced() throws Exception {
        TestUser librarian = librarian("rules");
        TestUser member = member("rules-member");
        TestUser other = member("rules-other");
        Long single = createBook(librarian, "Only copy", 1);
        issue(librarian, single, member);

        // No copies left.
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", single, other.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No copies of this book are available right now."));

        // Same book twice for one member.
        Long twoCopies = createBook(librarian, "Two copies", 2);
        issue(librarian, twoCopies, member);
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", twoCopies, member.email())))
                .andExpect(status().isConflict());

        // Maximum of 5 books.
        for (int i = 0; i < 3; i++) {
            issue(librarian, createBook(librarian, "Extra " + i, 1), member);
        }
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", createBook(librarian, "Sixth", 1), member.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This member already has the maximum of 5 books."));

        // Unknown member.
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", twoCopies, uniqueEmail("ghost"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void overdueLoansAreFlaggedAndBlockNewBorrowing() throws Exception {
        TestUser librarian = librarian("overdue");
        TestUser member = member("late");
        Long loanId = issue(librarian, createBook(librarian, "Late book", 1), member);
        jdbc.update("UPDATE loans SET issued_at = now() - interval '20 days', due_at = now() - interval '6 days' WHERE id = ?",
                loanId);

        mockMvc.perform(as(member, get("/api/v1/me/loans"))).andExpect(jsonPath("$.items[0].overdue").value(true));
        mockMvc.perform(as(librarian, get("/api/v1/admin/loans")).param("status", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + loanId + ")]").exists());
        mockMvc.perform(as(librarian, post("/api/v1/admin/loans")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"bookId":%d,"memberEmail":"%s"}""", createBook(librarian, "Next", 1), member.email())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This member has overdue books. They must be returned first."));
        mockMvc.perform(as(librarian, get("/api/v1/admin/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueLoans").isNumber());
    }

    @Test
    void theLastCopyCannotBeLentTwiceAtTheSameMoment() throws Exception {
        TestUser librarian = librarian("race");
        Long bookId = createBook(librarian, "Last copy", 1);
        List<TestUser> members = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            members.add(member("racer" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(members.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (TestUser m : members) {
                Callable<Integer> call = () -> mockMvc.perform(as(librarian, post("/api/v1/admin/loans"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("""
                                        {"bookId":%d,"memberEmail":"%s"}""", bookId, m.email())))
                        .andReturn().getResponse().getStatus();
                results.add(pool.submit(call));
            }
            int created = 0;
            for (Future<Integer> f : results) {
                int code = f.get();
                assertThat(code).isIn(201, 409);
                created += code == 201 ? 1 : 0;
            }
            assertThat(created).isEqualTo(1);
        }
        finally {
            pool.shutdownNow();
        }
        Integer available = jdbc.queryForObject("SELECT available_copies FROM books WHERE id = ?", Integer.class, bookId);
        assertThat(available).isZero();
    }
}
