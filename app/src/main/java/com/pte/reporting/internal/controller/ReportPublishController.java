package com.pte.reporting.internal.controller;

import com.pte.reporting.internal.service.ReportPublishService;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.dto.response.ReportPublicationReadinessResponse;
import com.pte.reporting.internal.dto.response.ReportPublicationSummaryResponse;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
 * reverse. The scheduling module now owns {@code POST
 * /api/v1/sessions/{id}/publish} for generating and scheduling an exam, so
 * this report-visibility command uses an explicit reporting namespace to keep
 * both commands unambiguous.
 */
@RestController
@RequestMapping("/api/v1/reporting/sessions")
@PreAuthorize("hasRole('HOST_ADMIN')")
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
        reportPublishService.publishSession(sessionPublicId, caller.tenantId(), caller.userId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{sessionPublicId}/preflight")
    public ApiResponse<ReportPublicationReadinessResponse> preflight(@PathVariable UUID sessionPublicId) {
        CurrentUser caller = currentUser();
        return ApiResponse.success(reportPublishService.preflight(sessionPublicId, caller.tenantId()));
    }

    @GetMapping("/{sessionPublicId}/publication")
    public ApiResponse<ReportPublicationSummaryResponse> publicationSummary(@PathVariable UUID sessionPublicId) {
        CurrentUser caller = currentUser();
        return ApiResponse.success(reportPublishService.publicationSummary(sessionPublicId, caller.tenantId()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException(ReportingConstants.AUTHENTICATED_PRINCIPAL_REQUIRED));
    }
}
