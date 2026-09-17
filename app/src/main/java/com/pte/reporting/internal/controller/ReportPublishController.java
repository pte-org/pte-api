package com.pte.reporting.internal.controller;

import com.pte.reporting.internal.service.ReportPublishService;
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
 * Origin of the host-gated publish command (ADR-002). Lives here (not on
 * {@code session}'s own controller) for the same reason as scoring's
 * command controller — dependency order runs session ──> attempt ──>
 * reporting: reporting is allowed to call session/attempt, never the
 * reverse. Route stays {@code POST /sessions/{id}/publish} to preserve the
 * pre-migration public API contract.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ReportPublishController {

    private final SessionService sessionService;
    private final ReportPublishService reportPublishService;

    public ReportPublishController(SessionService sessionService, ReportPublishService reportPublishService) {
        this.sessionService = sessionService;
        this.reportPublishService = reportPublishService;
    }

    @PostMapping("/{sessionPublicId}/publish")
    public ApiResponse<Void> requestPublish(@PathVariable UUID sessionPublicId) {
        CurrentUser caller = currentUser();
        sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
        reportPublishService.publishSession(sessionPublicId, caller.tenantId());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
