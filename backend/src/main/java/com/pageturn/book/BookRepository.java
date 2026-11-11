package com.pageturn.book;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    /**
     * Loads the book and locks its row until the transaction ends (SELECT ... FOR UPDATE), so two
     * librarians lending the last copy at the same moment can't both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    /** {@code pattern} is a ready LIKE pattern (see LikePatterns); always passed as a parameter. */
    @Query("""
            select b from Book b
            where (lower(b.title) like :pattern escape '\\'
                or lower(b.author) like :pattern escape '\\'
                or lower(coalesce(b.genre, '')) like :pattern escape '\\'
                or coalesce(b.isbn, '') like :pattern escape '\\')
              and (:availableOnly = false or b.availableCopies > 0)
            """)
    Page<Book> search(@Param("pattern") String pattern, @Param("availableOnly") boolean availableOnly,
            Pageable pageable);

    @Query("select coalesce(sum(b.totalCopies), 0) from Book b")
    long totalCopies();

    @Query("select coalesce(sum(b.availableCopies), 0) from Book b")
    long availableCopies();
}
