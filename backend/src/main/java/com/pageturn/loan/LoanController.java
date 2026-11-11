package com.pageturn.loan;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pageturn.common.security.AuthenticatedUser;
import com.pageturn.common.web.PageResponse;
import com.pageturn.loan.LoanDtos.IssueRequest;
import com.pageturn.loan.LoanDtos.LibraryStats;
import com.pageturn.loan.LoanDtos.LoanStatus;
import com.pageturn.loan.LoanDtos.LoanView;

import jakarta.validation.Valid;

@RestController
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    // --- Members: only their own loans ---

    /** GET /api/v1/me/loans?returned=false — the signed-in user's books (current, or history). */
    @GetMapping("/api/v1/me/loans")
    public PageResponse<LoanView> myLoans(@AuthenticationPrincipal AuthenticatedUser current,
            @RequestParam(defaultValue = "false") boolean returned,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return loanService.listForMember(current.id(), returned, page, size);
    }

    // --- Librarians ---

    @GetMapping("/api/v1/admin/loans")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PageResponse<LoanView> list(@RequestParam(defaultValue = "ACTIVE") LoanStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return loanService.listAll(status, page, size);
    }

    @PostMapping("/api/v1/admin/loans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanView issue(@Valid @RequestBody IssueRequest request, @AuthenticationPrincipal AuthenticatedUser current) {
        return loanService.issue(request, current.id());
    }

    @PostMapping("/api/v1/admin/loans/{id}/return")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanView returnLoan(@PathVariable Long id) {
        return loanService.returnLoan(id);
    }

    /** POST /api/v1/admin/loans/{id}/reminded — "I called this member about the book". */
    @PostMapping("/api/v1/admin/loans/{id}/reminded")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LoanView markReminded(@PathVariable Long id) {
        return loanService.markReminded(id);
    }

    @GetMapping("/api/v1/admin/stats")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public LibraryStats stats() {
        return loanService.stats();
    }
}
