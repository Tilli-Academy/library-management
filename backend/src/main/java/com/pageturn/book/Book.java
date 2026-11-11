package com.pageturn.book;

import java.time.LocalDate;

import com.pageturn.common.error.ApiException;
import com.pageturn.common.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A title in the catalogue, with how many physical copies the library owns and how many are on the shelf.
 * Copy counts only change through the methods below, while the row is locked (see BookRepository).
 */
@Entity
@Table(name = "books")
public class Book extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 13, unique = true)
    private String isbn;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 150)
    private String author;

    @Column(length = 60)
    private String genre;

    @Column(length = 2000)
    private String description;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies;

    @Column(name = "available_copies", nullable = false)
    private int availableCopies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status = BookStatus.AVAILABLE;

    /** Optional, for coming-soon books: when the library expects the copies to arrive. */
    @Column(name = "expected_arrival")
    private LocalDate expectedArrival;

    protected Book() {
        // for JPA
    }

    public Book(String isbn, String title, String author, String genre, String description, Integer publishedYear,
            int totalCopies) {
        setDetails(isbn, title, author, genre, description, publishedYear);
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /** A book announced as "coming soon": in the catalogue, but no copies yet. */
    public static Book comingSoon(String isbn, String title, String author, String genre, String description,
            Integer publishedYear, LocalDate expectedArrival) {
        Book book = new Book(isbn, title, author, genre, description, publishedYear, 0);
        book.status = BookStatus.COMING_SOON;
        book.expectedArrival = expectedArrival;
        return book;
    }

    /**
     * Switches between "coming soon" (no copies) and "in stock" (at least one copy).
     * A book can't go back to "coming soon" while copies of it are borrowed.
     */
    public void changeStock(boolean comingSoon, int totalCopiesIfInStock, LocalDate expectedArrival) {
        if (comingSoon) {
            if (copiesOnLoan() > 0) {
                throw ApiException.conflict("Copies of this book are on loan, so it can't be marked as coming soon.");
            }
            this.status = BookStatus.COMING_SOON;
            this.totalCopies = 0;
            this.availableCopies = 0;
            this.expectedArrival = expectedArrival;
            return;
        }
        if (totalCopiesIfInStock < 1) {
            throw ApiException.badRequest("A book in stock needs at least 1 copy.");
        }
        changeTotalCopies(totalCopiesIfInStock);
        this.status = BookStatus.AVAILABLE;
        this.expectedArrival = null;
    }

    public boolean isComingSoon() {
        return status == BookStatus.COMING_SOON;
    }

    public void setDetails(String isbn, String title, String author, String genre, String description,
            Integer publishedYear) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.description = description;
        this.publishedYear = publishedYear;
    }

    public int copiesOnLoan() {
        return totalCopies - availableCopies;
    }

    /** Changes how many copies the library owns; can't go below the number currently borrowed. */
    public void changeTotalCopies(int newTotal) {
        int onLoan = copiesOnLoan();
        if (newTotal < onLoan) {
            throw ApiException.conflict(onLoan + " copies are on loan, so the total can't be less than " + onLoan + ".");
        }
        this.totalCopies = newTotal;
        this.availableCopies = newTotal - onLoan;
    }

    public void takeCopy() {
        if (status == BookStatus.COMING_SOON) {
            throw ApiException.conflict("This book isn't in stock yet (coming soon).");
        }
        if (availableCopies <= 0) {
            throw ApiException.conflict("No copies of this book are available right now.");
        }
        availableCopies--;
    }

    public void putBackCopy() {
        if (availableCopies >= totalCopies) {
            throw new IllegalStateException("All copies of book " + id + " are already on the shelf.");
        }
        availableCopies++;
    }

    public Long getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getGenre() {
        return genre;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public BookStatus getStatus() {
        return status;
    }

    public LocalDate getExpectedArrival() {
        return expectedArrival;
    }
}
