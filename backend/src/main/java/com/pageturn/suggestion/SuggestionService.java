package com.pageturn.suggestion;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pageturn.book.Book;
import com.pageturn.book.BookRepository;
import com.pageturn.common.error.ApiException;
import com.pageturn.common.security.AuthenticatedUser;
import com.pageturn.common.web.PageResponse;
import com.pageturn.common.web.TextRules;
import com.pageturn.loan.LibraryProperties;
import com.pageturn.suggestion.SuggestionDtos.CreateSuggestionRequest;
import com.pageturn.suggestion.SuggestionDtos.SuggestionView;
import com.pageturn.user.UserRepository;

/**
 * Book requests: members ask for a book, others like it, and at {@code likes-to-stock} likes the request is
 * approved automatically — the book appears in the catalogue as "coming soon" for the librarians to order.
 */
@Service
public class SuggestionService {

    private final SuggestionRepository suggestions;
    private final BookRepository books;
    private final UserRepository users;
    private final LibraryProperties properties;

    public SuggestionService(SuggestionRepository suggestions, BookRepository books, UserRepository users,
            LibraryProperties properties) {
        this.suggestions = suggestions;
        this.books = books;
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public SuggestionView create(CreateSuggestionRequest request, AuthenticatedUser current) {
        String title = TextRules.clean(request.title());
        String author = TextRules.clean(request.author());
        // Lock the member's row so the "max open requests" rule holds even for requests sent at the same moment.
        users.findByIdForUpdate(current.id());
        if (suggestions.countByRequestedByIdAndStatus(current.id(), SuggestionStatus.OPEN)
                >= properties.maxOpenRequestsPerMember()) {
            throw ApiException.conflict("You already have " + properties.maxOpenRequestsPerMember()
                    + " open requests. Wait until some are decided before asking for more.");
        }
        if (books.findFirstByTitleIgnoreCaseAndAuthorIgnoreCase(title, author).isPresent()) {
            throw ApiException.conflict("This book is already in the catalogue.");
        }
        if (suggestions.existsByStatusAndTitleIgnoreCaseAndAuthorIgnoreCase(SuggestionStatus.OPEN, title, author)) {
            throw ApiException.conflict("Someone already requested this book. Find it in the list and like it instead.");
        }
        Instant now = Instant.now();
        Suggestion suggestion = suggestions.saveAndFlush(new Suggestion(title, author, TextRules.clean(request.note()),
                users.getReferenceById(current.id()), now));
        // Asking for a book counts as the first like.
        suggestions.addLike(suggestion.getId(), current.id(), now);
        suggestion.addLike();
        approveIfPopular(suggestion, now);
        return view(suggestions.saveAndFlush(suggestion), current, true);
    }

    @Transactional
    public SuggestionView like(Long id, AuthenticatedUser current) {
        Suggestion suggestion = lockOpen(id);
        Instant now = Instant.now();
        if (suggestions.addLike(id, current.id(), now) == 1) {
            suggestion.addLike();
            approveIfPopular(suggestion, now);
        }
        return view(suggestions.saveAndFlush(suggestion), current, true);
    }

    @Transactional
    public SuggestionView unlike(Long id, AuthenticatedUser current) {
        Suggestion suggestion = lockOpen(id);
        if (suggestions.removeLike(id, current.id()) == 1) {
            suggestion.removeLike();
        }
        return view(suggestions.saveAndFlush(suggestion), current, false);
    }

    /** Librarian decision: get the book now, whatever the number of likes. */
    @Transactional
    public SuggestionView approve(Long id, AuthenticatedUser current) {
        Suggestion suggestion = lockOpen(id);
        approveNow(suggestion, Instant.now());
        return view(suggestions.saveAndFlush(suggestion), current, suggestions.hasLiked(id, current.id()));
    }

    @Transactional
    public SuggestionView decline(Long id, AuthenticatedUser current) {
        Suggestion suggestion = lockOpen(id);
        suggestion.decline(Instant.now());
        return view(suggestions.saveAndFlush(suggestion), current, suggestions.hasLiked(id, current.id()));
    }

    @Transactional(readOnly = true)
    public PageResponse<SuggestionView> list(SuggestionStatus status, AuthenticatedUser current, int page, int size) {
        Sort sort = status == SuggestionStatus.OPEN
                ? Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by("createdAt", "id"))
                : Sort.by(Sort.Direction.DESC, "decidedAt", "id");
        Page<Suggestion> result = suggestions.findByStatus(status, PageResponse.pageable(page, size, sort));
        List<Long> ids = result.getContent().stream().map(Suggestion::getId).toList();
        Set<Long> liked = ids.isEmpty() ? Set.of() : new HashSet<>(suggestions.likedAmong(current.id(), ids));
        return PageResponse.of(result, s -> view(s, current, liked.contains(s.getId())));
    }

    private Suggestion lockOpen(Long id) {
        Suggestion suggestion = suggestions.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Request not found."));
        if (!suggestion.isOpen()) {
            throw ApiException.conflict(suggestion.getStatus() == SuggestionStatus.APPROVED
                    ? "This book is already being added to the library."
                    : "This request was declined by the library.");
        }
        return suggestion;
    }

    private void approveIfPopular(Suggestion suggestion, Instant now) {
        if (suggestion.getLikeCount() >= properties.likesToStock()) {
            approveNow(suggestion, now);
        }
    }

    /** Adds the book to the catalogue as "coming soon" (or links the existing entry if it's already there). */
    private void approveNow(Suggestion suggestion, Instant now) {
        Book book = books.findFirstByTitleIgnoreCaseAndAuthorIgnoreCase(suggestion.getTitle(), suggestion.getAuthor())
                .orElseGet(() -> books.saveAndFlush(Book.comingSoon(null, suggestion.getTitle(), suggestion.getAuthor(),
                        null, suggestion.getNote(), null, null)));
        suggestion.approve(book, now);
    }

    private SuggestionView view(Suggestion s, AuthenticatedUser current, boolean likedByMe) {
        UUID requesterId = s.getRequestedBy().getId();
        Book book = s.getBook();
        return new SuggestionView(s.getId(), s.getTitle(), s.getAuthor(), s.getNote(), s.getStatus(), s.getLikeCount(),
                properties.likesToStock(), likedByMe, requesterId.equals(current.id()),
                current.isLibrarian() ? s.getRequestedBy().getDisplayName() : null,
                book == null ? null : book.getId(), book == null ? null : book.getStatus(), s.getCreatedAt(),
                s.getDecidedAt());
    }
}
