package com.pageturn.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first librarian account, bound from {@code pageturn.admin.*} (environment variables).
 */
@ConfigurationProperties(prefix = "pageturn.admin")
public record AdminProperties(String email, String password, String name) {

    @Override
    public String toString() {
        return "AdminProperties[email=" + email + ", password=****]";
    }
}
