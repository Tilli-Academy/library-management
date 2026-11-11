package com.pageturn.common.security;

import java.util.UUID;

import com.pageturn.user.Role;

/**
 * The signed-in user for the current request, loaded fresh from the database on every request
 * (so a disabled account or a changed role takes effect immediately).
 */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public boolean isLibrarian() {
        return role == Role.LIBRARIAN;
    }
}
