package com.pageturn.book;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pageturn.book.BookDtos.BookRequest;
import com.pageturn.book.BookDtos.BookView;
import com.pageturn.common.web.PageResponse;

import jakarta.validation.Valid;

/**
 * Reading the catalogue: any signed-in user. Changing it: librarians only (under /api/v1/admin/).
 */
@RestController
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/api/v1/books")
    public PageResponse<BookView> search(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean available,
            @RequestParam(defaultValue = "false") boolean comingSoon,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return bookService.search(q, available, comingSoon, page, size);
    }

    @GetMapping("/api/v1/books/{id}")
    public BookView get(@PathVariable Long id) {
        return bookService.get(id);
    }

    @PostMapping("/api/v1/admin/books")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookView create(@Valid @RequestBody BookRequest request) {
        return bookService.create(request);
    }

    @PutMapping("/api/v1/admin/books/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookView update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/api/v1/admin/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('LIBRARIAN')")
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }
}
