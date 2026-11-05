package com.example.library.book;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookRepository repository;

    public BookController(BookRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Book> getBooks() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Book addBook(@Valid @RequestBody BookRequest request) {
        return repository.save(new Book(request.title().trim(),
            request.author() == null ? "" : request.author().trim()));
    }

    @PatchMapping("/{id}/issue")
    public Book issueBook(@PathVariable Long id) {
        Book book = findBook(id);
        if (book.isIssued()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This book is already issued.");
        }
        book.setIssued(true);
        return repository.save(book);
    }

    @PatchMapping("/{id}/return")
    public Book returnBook(@PathVariable Long id) {
        Book book = findBook(id);
        if (!book.isIssued()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This book is already available.");
        }
        book.setIssued(false);
        return repository.save(book);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable Long id) {
        Book book = findBook(id);
        if (book.isIssued()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Return this book before deleting it.");
        }
        repository.delete(book);
    }

    private Book findBook(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
    }
}
