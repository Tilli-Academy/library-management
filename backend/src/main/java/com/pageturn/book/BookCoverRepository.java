package com.pageturn.book;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCoverRepository extends JpaRepository<BookCover, Long> {
}
