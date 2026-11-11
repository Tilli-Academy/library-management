package com.pageturn.book;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.pageturn.ApiTestSupport;

class BookApiTest extends ApiTestSupport {

    @Test
    void librarianAddsEditsAndDeletesABook() throws Exception {
        TestUser librarian = librarian("books");
        String body = mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"978-0-13-468599-1","title":"  Effective Java ","author":"Joshua Bloch",
                                 "genre":"Programming","description":"Line one\\nLine two","publishedYear":2018,"totalCopies":3}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("9780134685991"))
                .andExpect(jsonPath("$.title").value("Effective Java"))
                .andExpect(jsonPath("$.description").value("Line one\nLine two"))
                .andExpect(jsonPath("$.availableCopies").value(3))
                .andReturn().getResponse().getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Effective Java, 3rd ed.","author":"Joshua Bloch","totalCopies":5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Effective Java, 3rd ed."))
                .andExpect(jsonPath("$.isbn").doesNotExist())
                .andExpect(jsonPath("$.totalCopies").value(5))
                .andExpect(jsonPath("$.availableCopies").value(5));

        mockMvc.perform(as(librarian, delete("/api/v1/admin/books/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(as(librarian, get("/api/v1/books/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void isbnsAreValidatedAndUnique() throws Exception {
        TestUser librarian = librarian("isbn");
        String isbn = "0-306-40615-2";
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn":"0-306-40615-3","title":"Typo","author":"A","totalCopies":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("ISBN is not valid. Check the digits (10 or 13 digits)."));
        String valid = json("""
                {"isbn":"%s","title":"First","author":"A","totalCopies":1}""", isbn);
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated());
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
    }

    @Test
    void searchFiltersAndPagesWithASafeMaximumPageSize() throws Exception {
        TestUser librarian = librarian("search");
        String tag = "zq" + System.nanoTime();
        createBook(librarian, tag + " Alpha", 1);
        Long beta = createBook(librarian, tag + " Beta", 1);
        createBook(librarian, tag + " Gamma 100%_", 2);
        TestUser member = member("reader");
        issue(librarian, beta, member);

        mockMvc.perform(as(member, get("/api/v1/books")).param("q", tag.toUpperCase()))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].title").value(tag + " Alpha"));
        mockMvc.perform(as(member, get("/api/v1/books")).param("q", tag).param("available", "true"))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(as(member, get("/api/v1/books")).param("q", "100%_"))
                .andExpect(jsonPath("$.items[0].title").value(tag + " Gamma 100%_"));
        mockMvc.perform(as(member, get("/api/v1/books")).param("size", "100000").param("page", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void booksWithBorrowingHistoryCannotBeDeletedAndCopiesCannotDropBelowLoans() throws Exception {
        TestUser librarian = librarian("history");
        TestUser member = member("borrower");
        Long bookId = createBook(librarian, "Popular", 2);
        issue(librarian, bookId, member);

        mockMvc.perform(as(librarian, delete("/api/v1/admin/books/" + bookId))).andExpect(status().isConflict());
        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Popular","author":"Test Author","totalCopies":1}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(0));
        mockMvc.perform(as(librarian, put("/api/v1/admin/books/" + bookId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Popular","author":"Test Author","totalCopies":0}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBookDataGetsFieldErrors() throws Exception {
        TestUser librarian = librarian("invalid");
        mockMvc.perform(as(librarian, post("/api/v1/admin/books")).contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"title":"%s","author":"","publishedYear":99999,"totalCopies":5000}""", "x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title must be at most 200 characters."))
                .andExpect(jsonPath("$.errors.author").value("Author is required."))
                .andExpect(jsonPath("$.errors.publishedYear").value("Enter a valid year."))
                .andExpect(jsonPath("$.errors.totalCopies").value("At most 1000 copies."));
    }
}
