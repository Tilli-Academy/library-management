package com.pageturn.suggestion;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pageturn.common.security.AuthenticatedUser;
import com.pageturn.common.web.PageResponse;
import com.pageturn.suggestion.SuggestionDtos.CreateSuggestionRequest;
import com.pageturn.suggestion.SuggestionDtos.SuggestionView;

import jakarta.validation.Valid;

/**
 * Book requests. Any signed-in user can request and like; only librarians approve or decline by hand.
 */
@RestController
public class SuggestionController {

    private final SuggestionService service;

    public SuggestionController(SuggestionService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/suggestions")
    public PageResponse<SuggestionView> list(@AuthenticationPrincipal AuthenticatedUser current,
            @RequestParam(defaultValue = "OPEN") SuggestionStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(status, current, page, size);
    }

    @PostMapping("/api/v1/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionView create(@Valid @RequestBody CreateSuggestionRequest request,
            @AuthenticationPrincipal AuthenticatedUser current) {
        return service.create(request, current);
    }

    @PostMapping("/api/v1/suggestions/{id}/like")
    public SuggestionView like(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser current) {
        return service.like(id, current);
    }

    @DeleteMapping("/api/v1/suggestions/{id}/like")
    public SuggestionView unlike(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser current) {
        return service.unlike(id, current);
    }

    @PostMapping("/api/v1/admin/suggestions/{id}/approve")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public SuggestionView approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser current) {
        return service.approve(id, current);
    }

    @PostMapping("/api/v1/admin/suggestions/{id}/decline")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public SuggestionView decline(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser current) {
        return service.decline(id, current);
    }
}
