package com.pageturn.suggestion;

import java.time.Instant;

import com.pageturn.book.BookStatus;
import com.pageturn.common.web.TextRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SuggestionDtos {

    private SuggestionDtos() {
    }

    public record CreateSuggestionRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be at most 200 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Title contains invalid characters.") String title,
            @NotBlank(message = "Author is required.")
            @Size(max = 150, message = "Author must be at most 150 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Author contains invalid characters.") String author,
            @Size(max = 500, message = "Note must be at most 500 characters.")
            @Pattern(regexp = TextRules.MULTI_LINE, message = "Note contains invalid characters.") String note) {
    }

    /**
     * A request as shown in the list. {@code requestedByName} is only filled in for librarians — members
     * don't see who asked for what.
     */
    public record SuggestionView(Long id, String title, String author, String note, SuggestionStatus status,
            int likeCount, int likesNeeded, boolean likedByMe, boolean mine, String requestedByName, Long bookId,
            BookStatus bookStatus, Instant createdAt, Instant decidedAt) {
    }
}
