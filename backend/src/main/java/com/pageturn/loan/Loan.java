package com.pageturn.loan;

import java.time.Instant;

import com.pageturn.book.Book;
import com.pageturn.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One copy of a book lent to one member. Active while {@code returnedAt} is null.
 */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private UserAccount member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id")
    private UserAccount issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected Loan() {
        // for JPA
    }

    public Loan(Book book, UserAccount member, UserAccount issuedBy, Instant issuedAt, Instant dueAt) {
        this.book = book;
        this.member = member;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    public boolean isOverdue(Instant now) {
        return returnedAt == null && dueAt.isBefore(now);
    }

    void markReturned(Instant when) {
        this.returnedAt = when;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public UserAccount getMember() {
        return member;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
