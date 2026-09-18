package com.pte.scoring.internal.controller;

import com.pte.scoring.internal.service.ScoringCommandService;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Origin of the host-gated scoring command (ADR-002). A host decides when to
 * trigger scoring — scoring never self-triggers on submission. Lives here
 * (not on {@code session}'s own controller) because dependency order runs
 * session ──> attempt ──> scoring: scoring is allowed to call session/attempt,
 * never the reverse, so the command's implementation has to be scoring's own.
 * Route stays {@code POST /sessions/{id}/score} to preserve the pre-migration
 * public API contract.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ScoringCommandController {

    private final SessionService sessionService;
    private final ScoringCommandService scoringCommandService;

    public ScoringCommandController(SessionService sessionService, ScoringCommandService scoringCommandService) {
        this.sessionService = sessionService;
        this.scoringCommandService = scoringCommandService;
    }

    @PostMapping("/{sessionPublicId}/score")
    public ApiResponse<Void> requestScoring(@PathVariable UUID sessionPublicId) {
        CurrentUser caller = currentUser();
        sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
        scoringCommandService.requestScoring(sessionPublicId, caller.tenantId());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
