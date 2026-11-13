package com.pageturn.loan;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lending rules, bound from {@code pageturn.library.*}.
 */
@ConfigurationProperties(prefix = "pageturn.library")
public record LibraryProperties(int loanDays, int maxActiveLoansPerMember, int likesToStock,
        int maxOpenRequestsPerMember, java.time.Duration minAccountAgeForRequests) {

    public LibraryProperties {
        if (loanDays < 1 || loanDays > 365) {
            throw new IllegalStateException("pageturn.library.loan-days must be between 1 and 365.");
        }
        if (maxActiveLoansPerMember < 1) {
            throw new IllegalStateException("pageturn.library.max-active-loans-per-member must be at least 1.");
        }
        if (likesToStock < 1) {
            throw new IllegalStateException("pageturn.library.likes-to-stock must be at least 1.");
        }
        if (minAccountAgeForRequests == null || minAccountAgeForRequests.isNegative()) {
            minAccountAgeForRequests = java.time.Duration.ofHours(24);
        }
        if (maxOpenRequestsPerMember < 1) {
            throw new IllegalStateException("pageturn.library.max-open-requests-per-member must be at least 1.");
        }
    }
}
