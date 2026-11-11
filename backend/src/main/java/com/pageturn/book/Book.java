package com.pageturn.book;

import com.pageturn.common.error.ApiException;
import com.pageturn.common.persistence.TimestampedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Book() {
        // for JPA
    }

    public Book(String isbn, String title, String author, String genre, String description, Integer publishedYear,
            int totalCopies) {
        setDetails(isbn, title, author, genre, description, publishedYear);
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
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
}
