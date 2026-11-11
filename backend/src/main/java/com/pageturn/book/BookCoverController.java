package com.pageturn.book;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pageturn.book.BookDtos.BookView;
import com.pageturn.common.error.ApiException;

/**
 * Book cover pictures. Anyone signed in can see them; only librarians upload or remove them.
 */
@RestController
public class BookCoverController {

    private final BookRepository books;
    private final BookCoverRepository covers;
    private final BookService bookService;

    public BookCoverController(BookRepository books, BookCoverRepository covers, BookService bookService) {
        this.books = books;
        this.covers = covers;
        this.bookService = bookService;
    }

    /** GET /api/v1/books/{id}/cover — the picture. The URL carries ?v=version, so it can be cached for long. */
    @GetMapping("/api/v1/books/{id}/cover")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        BookCover cover = covers.findById(id).orElseThrow(() -> ApiException.notFound("This book has no cover."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cover.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("cover.jpg").build().toString())
                .body(cover.getData());
    }

    /** PUT /api/v1/admin/books/{id}/cover — upload a JPG or PNG (form field "file", max 3 MB). */
    @PutMapping("/api/v1/admin/books/{id}/cover")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional
    public BookView upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > 3 * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "The picture must be 3 MB or smaller.");
        }
        byte[] clean = CoverImages.toSafeJpeg(file.getBytes()); // before locking: decoding takes a moment
        Book book = books.findByIdForUpdate(id).orElseThrow(BookService::notFound);
        Instant now = Instant.now();
        covers.save(new BookCover(id, CoverImages.CONTENT_TYPE, clean, now));
        book.setCoverUpdatedAt(now);
        books.saveAndFlush(book);
        return bookService.get(id);
    }

    @DeleteMapping("/api/v1/admin/books/{id}/cover")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void remove(@PathVariable Long id) {
        Book book = books.findByIdForUpdate(id).orElseThrow(BookService::notFound);
        covers.deleteById(id);
        book.setCoverUpdatedAt(null);
        books.saveAndFlush(book);
    }
}
