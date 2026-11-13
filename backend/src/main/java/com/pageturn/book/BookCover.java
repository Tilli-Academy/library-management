package com.pageturn.book;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The (cleaned, resized) cover picture of one book. Kept apart from Book so lists never load image bytes. */
@Entity
@Table(name = "book_covers")
public class BookCover {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookCover() {
        // for JPA
    }

    BookCover(Long bookId, String contentType, byte[] data, Instant updatedAt) {
        this.bookId = bookId;
        this.contentType = contentType;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }
}
