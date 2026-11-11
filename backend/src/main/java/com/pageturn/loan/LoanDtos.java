package com.pageturn.loan;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LoanDtos {

    private LoanDtos() {
    }

    public enum LoanStatus {
        ACTIVE, OVERDUE, RETURNED
    }

    public record IssueRequest(
            @NotNull(message = "Choose a book.") Long bookId,
            @NotBlank(message = "Member email is required.") @Email(message = "Enter a valid email address.")
            @Size(max = 320, message = "Email is too long.") String memberEmail) {
    }

    public record LoanView(Long id, Long bookId, String bookTitle, String bookAuthor, UUID memberId,
            String memberName, String memberEmail, String memberPhone, Instant issuedAt, Instant dueAt,
            Instant returnedAt, Instant lastRemindedAt, boolean overdue) {

        public static LoanView of(Loan loan, Instant now) {
            return new LoanView(loan.getId(), loan.getBook().getId(), loan.getBook().getTitle(),
                    loan.getBook().getAuthor(), loan.getMember().getId(), loan.getMember().getDisplayName(),
                    loan.getMember().getEmail(), loan.getMember().getPhone(), loan.getIssuedAt(), loan.getDueAt(),
                    loan.getReturnedAt(), loan.getLastRemindedAt(), loan.isOverdue(now));
        }
    }

    public record LibraryStats(long titles, long totalCopies, long availableCopies, long activeLoans,
            long overdueLoans, long members) {
    }
}
