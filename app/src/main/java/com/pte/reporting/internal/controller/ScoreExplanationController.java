package com.pte.reporting.internal.controller;

import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.dto.response.ScoreExplanationResponse;
import com.pte.reporting.internal.service.explain.ScoreExplanationService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Score explanation of one attempt (FR-07) on tenant-web. Only a Host (own tenant) and an Examiner (assigned to the
 * attempt) may read it; every other role — Platform Admin and every vendor role included — gets 403 here, and the
 * tenant/assignment check in {@link ScoreExplanationService} answers 404 for anything the caller may not see.
 * There is no parameter to choose another rubric version: recomputation always uses each answer's pinned version.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('HOST_ADMIN','EXAMINER')")
public class ScoreExplanationController {

    private final ScoreExplanationService explanationService;

    public ScoreExplanationController(ScoreExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @GetMapping(ReportingConstants.SCORE_EXPLANATION_PATH)
    public ApiResponse<ScoreExplanationResponse> getScoreExplanation(@PathVariable UUID attemptPublicId) {
        return ApiResponse.success(explanationService.explain(attemptPublicId,
                CurrentUserContext.current().orElseThrow(() ->
                        new IllegalStateException(ReportingConstants.AUTHENTICATED_PRINCIPAL_REQUIRED))));
    }
}
