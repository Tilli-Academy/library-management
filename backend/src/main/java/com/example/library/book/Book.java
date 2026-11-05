package com.example.library.book;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(length = 120)
    private String author;
    @Column(nullable = false)
    private boolean issued = false;

    protected Book() {}
    public Book(String title, String author) {
        this.title = title;
        this.author = author;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public boolean isIssued() { return issued; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setIssued(boolean issued) { this.issued = issued; }
}
