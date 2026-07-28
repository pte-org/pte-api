package com.pte.scoring.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scoring.dto.response.ScoringAnswerPageResponse;
import com.pte.scoring.dto.response.ScoringAnswerResponse;
import com.pte.scoring.service.ScoringReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** scoring's first human-facing endpoint (phase-09): host approves an AI-scored, review-required answer. */
@RestController
@RequestMapping("/answers")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ScoringReviewController {

    private final ScoringReviewService scoringReviewService;

    public ScoringReviewController(ScoringReviewService scoringReviewService) {
        this.scoringReviewService = scoringReviewService;
    }

    @GetMapping("/reviews")
    public ApiResponse<ScoringAnswerPageResponse> listPending(
            @RequestParam UUID sessionPublicId,
            @RequestParam(defaultValue = "AI_SCORED_PENDING_REVIEW") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                scoringReviewService.listPending(sessionPublicId, status, page, size, currentUser())
        );
    }

    @PostMapping("/{answerPublicId}/review")
    public ApiResponse<ScoringAnswerResponse> approve(@PathVariable UUID answerPublicId) {
        return ApiResponse.success(scoringReviewService.approve(answerPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
