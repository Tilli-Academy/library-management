package com.pageturn.book;

import java.time.Instant;

import com.pageturn.common.web.TextRules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BookDtos {

    private BookDtos() {
    }

    /** Used for both "add book" and "edit book". Every field has a length limit and a character rule. */
    public record BookRequest(
            @Size(max = 17, message = "ISBN is too long.")
            @Pattern(regexp = "^[0-9Xx\\- ]*$", message = "ISBN may only contain digits, X, spaces and dashes.")
            String isbn,
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be at most 200 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Title contains invalid characters.") String title,
            @NotBlank(message = "Author is required.")
            @Size(max = 150, message = "Author must be at most 150 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Author contains invalid characters.") String author,
            @Size(max = 60, message = "Genre must be at most 60 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Genre contains invalid characters.") String genre,
            @Size(max = 2000, message = "Description must be at most 2000 characters.")
            @Pattern(regexp = TextRules.MULTI_LINE, message = "Description contains invalid characters.")
            String description,
            @Min(value = 1000, message = "Enter a valid year.") @Max(value = 2100, message = "Enter a valid year.")
            Integer publishedYear,
            @NotNull(message = "Number of copies is required.")
            @Min(value = 1, message = "At least 1 copy.") @Max(value = 1000, message = "At most 1000 copies.")
            Integer totalCopies) {
    }

    /**
     * {@code expectedBackAt}: only when no copy is on the shelf — the earliest due date of the borrowed copies,
     * i.e. when the book should be available again. May be in the past if that copy is overdue.
     */
    public record BookView(Long id, String isbn, String title, String author, String genre, String description,
            Integer publishedYear, int totalCopies, int availableCopies, Instant expectedBackAt) {

        public static BookView of(Book book, Instant expectedBackAt) {
            return new BookView(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(), book.getGenre(),
                    book.getDescription(), book.getPublishedYear(), book.getTotalCopies(), book.getAvailableCopies(),
                    book.getAvailableCopies() > 0 ? null : expectedBackAt);
        }
    }
}
