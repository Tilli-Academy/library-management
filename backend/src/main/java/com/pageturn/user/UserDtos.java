package com.pageturn.user;

import java.time.Instant;
import java.util.UUID;

import com.pageturn.common.web.TextRules;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for accounts. Requests are separate classes from the database entity,
 * so a client can never set fields it must not control (like "role" or "enabled") — no mass assignment.
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Enter a valid email address.")
            @Size(max = 320, message = "Email is too long.") String email,
            @NotBlank(message = "Password is required.")
            @Size(min = 12, max = 72, message = "Password must be 12 to 72 characters.") String password,
            @NotBlank(message = "Your name is required.")
            @Size(max = 100, message = "Name must be at most 100 characters.")
            @Pattern(regexp = TextRules.SINGLE_LINE, message = "Name contains invalid characters.") String displayName,
            @NotBlank(message = "Phone number is required, so the library can contact you about your books.")
            @Pattern(regexp = TextRules.PHONE, message = "Enter a valid phone number, e.g. +91 98765 43210.")
            String phone) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=****]";
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.") @Size(max = 320) String email,
            @NotBlank(message = "Password is required.") @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=****]";
        }
    }

    /** What users may change about themselves. */
    public record UpdateProfileRequest(
            @NotBlank(message = "Phone number is required.")
            @Pattern(regexp = TextRules.PHONE, message = "Enter a valid phone number, e.g. +91 98765 43210.")
            String phone) {
    }

    /** What a librarian may change about another user. Both fields optional. */
    public record UpdateUserRequest(Role role, Boolean enabled) {
    }

    /** A user as shown to themselves or to a librarian. Never includes the password hash. */
    public record UserView(UUID id, String email, String displayName, String phone, Role role, boolean enabled,
            Instant createdAt) {

        public static UserView of(UserAccount user) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getPhone(), user.getRole(),
                    user.isEnabled(), user.getCreatedAt());
        }
    }
}
