package com.pageturn.suggestion;

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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    /** Locks the request row: likes are counted one at a time, so the count is always exact. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suggestion s where s.id = :id")
    Optional<Suggestion> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"requestedBy", "book"})
    Page<Suggestion> findByStatus(SuggestionStatus status, Pageable pageable);

    long countByRequestedByIdAndStatus(UUID userId, SuggestionStatus status);

    boolean existsByStatusAndTitleIgnoreCaseAndAuthorIgnoreCase(SuggestionStatus status, String title, String author);

    long countByStatus(SuggestionStatus status);

    // --- Likes (separate table; one row per member per request) ---

    @Query(value = "SELECT EXISTS (SELECT 1 FROM book_suggestion_likes WHERE suggestion_id = :sid AND user_id = :uid)",
            nativeQuery = true)
    boolean hasLiked(@Param("sid") Long suggestionId, @Param("uid") UUID userId);

    /** Adds a like; does nothing if this member already liked it. Returns 1 if a like was added, else 0. */
    @Modifying
    @Query(value = """
            INSERT INTO book_suggestion_likes (suggestion_id, user_id, created_at) VALUES (:sid, :uid, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addLike(@Param("sid") Long suggestionId, @Param("uid") UUID userId, @Param("now") Instant now);

    /** Returns 1 if a like was removed, else 0. */
    @Modifying
    @Query(value = "DELETE FROM book_suggestion_likes WHERE suggestion_id = :sid AND user_id = :uid", nativeQuery = true)
    int removeLike(@Param("sid") Long suggestionId, @Param("uid") UUID userId);

    /** Which of these requests has this member liked? (one query per page) */
    @Query(value = "SELECT suggestion_id FROM book_suggestion_likes WHERE user_id = :uid AND suggestion_id IN (:ids)",
            nativeQuery = true)
    List<Long> likedAmong(@Param("uid") UUID userId, @Param("ids") Collection<Long> suggestionIds);
}
