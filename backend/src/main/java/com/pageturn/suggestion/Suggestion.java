package com.pageturn.suggestion;

import java.time.Instant;

import com.pageturn.book.Book;
import com.pageturn.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A member's request for a book the library doesn't have. Other members like it; with enough likes the
 * library gets it. {@code likeCount} only changes while the row is locked (see SuggestionRepository).
 */
@Entity
@Table(name = "book_suggestions")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 150)
    private String author;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private UserAccount requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.OPEN;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    /** The catalogue entry created (or found) when the request was approved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Suggestion() {
        // for JPA
    }

    public Suggestion(String title, String author, String note, UserAccount requestedBy, Instant now) {
        this.title = title;
        this.author = author;
        this.note = note;
        this.requestedBy = requestedBy;
        this.createdAt = now;
    }

    void addLike() {
        likeCount++;
    }

    void removeLike() {
        likeCount = Math.max(0, likeCount - 1);
    }

    void approve(Book book, Instant now) {
        this.status = SuggestionStatus.APPROVED;
        this.book = book;
        this.decidedAt = now;
    }

    void decline(Instant now) {
        this.status = SuggestionStatus.DECLINED;
        this.decidedAt = now;
    }

    public boolean isOpen() {
        return status == SuggestionStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getNote() {
        return note;
    }

    public UserAccount getRequestedBy() {
        return requestedBy;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public Book getBook() {
        return book;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
