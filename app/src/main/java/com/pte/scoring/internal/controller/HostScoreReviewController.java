package com.pte.scoring.internal.controller;

import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.dto.response.HostScoreReviewResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionPreviewResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionResultResponse;
import com.pte.scoring.dto.response.ScoreSourceAuditResponse;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/scoring/sessions")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostScoreReviewController {

    private final ScoringService scoringService;
    private final SessionService sessionService;

    public HostScoreReviewController(ScoringService scoringService, SessionService sessionService) {
        this.scoringService = scoringService;
        this.sessionService = sessionService;
    }

    @GetMapping("/{sessionPublicId}/review")
    public ApiResponse<HostScoreReviewResponse> getReview(@PathVariable UUID sessionPublicId) {
        CurrentUser caller = currentUser();
        sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
        return ApiResponse.success(scoringService.getHostScoreReview(caller.tenantId(), sessionPublicId));
    }

    @PostMapping("/{sessionPublicId}/source-selection/preview")
    public ApiResponse<ScoreSourceSelectionPreviewResponse> preview(@PathVariable UUID sessionPublicId,
            @RequestBody SelectScoreSourceRequest request) {
        return ApiResponse.success(scoringService.previewScoreSourceSelection(sessionPublicId, request, currentUser()));
    }

    @PostMapping("/{sessionPublicId}/source-selection/apply")
    public ApiResponse<ScoreSourceSelectionResultResponse> apply(@PathVariable UUID sessionPublicId,
            @RequestBody SelectScoreSourceRequest request) {
        return ApiResponse.success(scoringService.applyScoreSourceSelection(sessionPublicId, request, currentUser()));
    }

    @GetMapping("/{sessionPublicId}/source-selection/audits")
    public ApiResponse<List<ScoreSourceAuditResponse>> getSourceSelectionAudits(
            @PathVariable UUID sessionPublicId) {
        return ApiResponse.success(scoringService.getScoreSourceSelectionAudits(sessionPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException(ScoringConstants.AUTHENTICATED_PRINCIPAL_REQUIRED));
    }
}
