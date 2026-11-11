package com.pageturn.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Loads the user and locks the row until the transaction ends (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    /** Search by name or email. {@code pattern} is a ready LIKE pattern with wildcards escaped. */
    @Query("""
            select u from UserAccount u
            where lower(u.displayName) like :pattern escape '\\'
               or u.email like :pattern escape '\\'
            """)
    Page<UserAccount> search(@Param("pattern") String pattern, Pageable pageable);
}
