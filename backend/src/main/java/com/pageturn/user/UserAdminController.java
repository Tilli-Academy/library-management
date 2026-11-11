package com.pageturn.user;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pageturn.common.error.ApiException;
import com.pageturn.common.persistence.LikePatterns;
import com.pageturn.common.security.AuthenticatedUser;
import com.pageturn.common.web.PageResponse;
import com.pageturn.user.UserDtos.UpdateUserRequest;
import com.pageturn.user.UserDtos.UserView;

/**
 * Librarian-only: list members, promote/demote, and block/unblock accounts.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('LIBRARIAN')")
public class UserAdminController {

    private final UserRepository users;

    public UserAdminController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<UserView> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(users.search(LikePatterns.contains(q),
                PageResponse.pageable(page, size, Sort.by("displayName", "email"))), UserView::of);
    }

    @PatchMapping("/{id}")
    @Transactional
    public UserView update(@PathVariable UUID id, @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser current) {
        if (current.id().equals(id)) {
            // Prevents a librarian from locking themselves (and possibly everyone) out by accident.
            throw ApiException.badRequest("You cannot change your own role or status.");
        }
        // Lock both accounts (always in the same order, so two requests can't deadlock). Then re-check that
        // the person making the change is STILL an active librarian: if two librarians try to demote or
        // block each other at the same moment, the second one is refused, so at least one always remains.
        UUID first = current.id().compareTo(id) < 0 ? current.id() : id;
        UUID second = first.equals(id) ? current.id() : id;
        users.findByIdForUpdate(first);
        users.findByIdForUpdate(second);
        UserAccount actor = users.findById(current.id()).orElseThrow(() -> ApiException.forbidden("Account not found."));
        if (!actor.isEnabled() || actor.getRole() != Role.LIBRARIAN) {
            throw ApiException.forbidden("You do not have permission to do this.");
        }
        UserAccount user = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.setEnabled(request.enabled());
            if (!request.enabled()) {
                user.revokeSessions();
            }
        }
        return UserView.of(users.saveAndFlush(user));
    }
}
