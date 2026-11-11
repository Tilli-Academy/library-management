package com.pageturn.loan;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByBookId(Long bookId);

    boolean existsByBookIdAndMemberIdAndReturnedAtIsNull(Long bookId, UUID memberId);

    boolean existsByMemberIdAndReturnedAtIsNullAndDueAtBefore(UUID memberId, Instant now);

    long countByMemberIdAndReturnedAtIsNull(UUID memberId);

    long countByReturnedAtIsNull();

    long countByReturnedAtIsNullAndDueAtBefore(Instant now);

    /** For each book id: the earliest due date of its copies still on loan (= when one should be back). */
    @Query("""
            select l.book.id, min(l.dueAt) from Loan l
            where l.returnedAt is null and l.book.id in :bookIds
            group by l.book.id
            """)
    List<Object[]> earliestDueByBook(@Param("bookIds") Collection<Long> bookIds);

    /** Locks the loan row so the same loan can't be returned twice at the same moment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    // --- Librarian lists (book and member loaded in the same query) ---

    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findByReturnedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findByReturnedAtIsNullAndDueAtBefore(Instant now, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findByReturnedAtIsNotNull(Pageable pageable);

    // --- A member's own loans: always filtered by the signed-in user's id ---

    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findByMemberIdAndReturnedAtIsNull(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findByMemberIdAndReturnedAtIsNotNull(UUID memberId, Pageable pageable);
}
