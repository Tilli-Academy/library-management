package com.pageturn.loan;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pageturn.book.Book;
import com.pageturn.book.BookRepository;
import com.pageturn.common.error.ApiException;
import com.pageturn.common.web.PageResponse;
import com.pageturn.loan.LoanDtos.IssueRequest;
import com.pageturn.loan.LoanDtos.LibraryStats;
import com.pageturn.loan.LoanDtos.LoanStatus;
import com.pageturn.loan.LoanDtos.LoanView;
import com.pageturn.user.Role;
import com.pageturn.user.UserAccount;
import com.pageturn.user.UserRepository;

/**
 * Lending rules. Rows are locked in a fixed order (member, then book; or loan, then book) so concurrent
 * requests can't oversell copies or return a book twice, and can't deadlock each other.
 */
@Service
public class LoanService {

    private final LoanRepository loans;
    private final BookRepository books;
    private final UserRepository users;
    private final LibraryProperties properties;

    public LoanService(LoanRepository loans, BookRepository books, UserRepository users,
            LibraryProperties properties) {
        this.loans = loans;
        this.books = books;
        this.users = users;
        this.properties = properties;
    }

    @Transactional
    public LoanView issue(IssueRequest request, UUID librarianId) {
        String email = UserAccount.normalizeEmail(request.memberEmail());
        UUID memberId = users.findByEmail(email).map(UserAccount::getId)
                .orElseThrow(() -> ApiException.notFound("No member with this email address."));
        // Lock the member row: two loans for the same member are processed one after the other,
        // so the "maximum books" rule can't be bypassed by sending requests at the same moment.
        UserAccount member = users.findByIdForUpdate(memberId)
                .orElseThrow(() -> ApiException.notFound("No member with this email address."));
        if (!member.isEnabled()) {
            throw ApiException.conflict("This member's account is blocked.");
        }

        Book book = books.findByIdForUpdate(request.bookId())
                .orElseThrow(() -> ApiException.notFound("Book not found."));
        Instant now = Instant.now();
        if (loans.existsByBookIdAndMemberIdAndReturnedAtIsNull(book.getId(), member.getId())) {
            throw ApiException.conflict("This member already has a copy of this book.");
        }
        if (loans.existsByMemberIdAndReturnedAtIsNullAndDueAtBefore(member.getId(), now)) {
            throw ApiException.conflict("This member has overdue books. They must be returned first.");
        }
        if (loans.countByMemberIdAndReturnedAtIsNull(member.getId()) >= properties.maxActiveLoansPerMember()) {
            throw ApiException.conflict("This member already has the maximum of "
                    + properties.maxActiveLoansPerMember() + " books.");
        }
        book.takeCopy();
        UserAccount librarian = users.getReferenceById(librarianId);
        Loan loan = loans.saveAndFlush(new Loan(book, member, librarian, now,
                now.plus(Duration.ofDays(properties.loanDays()))));
        return LoanView.of(loan, now);
    }

    @Transactional
    public LoanView returnLoan(Long loanId) {
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> ApiException.notFound("Loan not found."));
        if (!loan.isActive()) {
            throw ApiException.conflict("This book has already been returned.");
        }
        Book book = books.findByIdForUpdate(loan.getBook().getId())
                .orElseThrow(() -> new IllegalStateException("Loan " + loanId + " points to a missing book."));
        Instant now = Instant.now();
        loan.markReturned(now);
        book.putBackCopy();
        return LoanView.of(loans.saveAndFlush(loan), now);
    }

    /** Records that a librarian contacted the member about this (still borrowed) book. */
    @Transactional
    public LoanView markReminded(Long loanId) {
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> ApiException.notFound("Loan not found."));
        if (!loan.isActive()) {
            throw ApiException.conflict("This book has already been returned.");
        }
        Instant now = Instant.now();
        loan.markReminded(now);
        return LoanView.of(loans.saveAndFlush(loan), now);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoanView> listAll(LoanStatus status, int page, int size) {
        Instant now = Instant.now();
        Page<Loan> result = switch (status) {
            case ACTIVE -> loans.findByReturnedAtIsNull(pageable(page, size, Sort.by("dueAt", "id")));
            case OVERDUE -> loans.findByReturnedAtIsNullAndDueAtBefore(now, pageable(page, size, Sort.by("dueAt", "id")));
            case RETURNED -> loans.findByReturnedAtIsNotNull(
                    pageable(page, size, Sort.by(Sort.Direction.DESC, "returnedAt", "id")));
        };
        return PageResponse.of(result, loan -> LoanView.of(loan, now));
    }

    /** Only ever the signed-in member's own loans: the id comes from the session, never from the request. */
    @Transactional(readOnly = true)
    public PageResponse<LoanView> listForMember(UUID memberId, boolean returned, int page, int size) {
        Instant now = Instant.now();
        Page<Loan> result = returned
                ? loans.findByMemberIdAndReturnedAtIsNotNull(memberId,
                        pageable(page, size, Sort.by(Sort.Direction.DESC, "returnedAt", "id")))
                : loans.findByMemberIdAndReturnedAtIsNull(memberId, pageable(page, size, Sort.by("dueAt", "id")));
        return PageResponse.of(result, loan -> LoanView.of(loan, now));
    }

    @Transactional(readOnly = true)
    public LibraryStats stats() {
        Instant now = Instant.now();
        return new LibraryStats(books.count(), books.totalCopies(), books.availableCopies(),
                loans.countByReturnedAtIsNull(), loans.countByReturnedAtIsNullAndDueAtBefore(now),
                users.countByRole(Role.MEMBER));
    }

    private static Pageable pageable(int page, int size, Sort sort) {
        return PageResponse.pageable(page, size, sort);
    }
}
