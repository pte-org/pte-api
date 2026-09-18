package com.pte.scoring.internal.controller;

import com.pte.scoring.internal.dto.request.SubmitTeacherScoreRequest;
import com.pte.scoring.internal.dto.response.AnswerListResponse;
import com.pte.scoring.internal.dto.response.AnswerReviewDetailResponse;
import com.pte.scoring.internal.dto.response.ScoringAnswerResponse;
import com.pte.scoring.internal.service.ScoringReviewService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * scoring's human-facing review endpoints: a host lists and inspects a
 * tenant's submitted answers, and records their own independent score. No
 * approval gate — an AI score always finalizes on its own; a host's score is
 * parallel data, never a gate.
 */
@RestController
@RequestMapping("/api/v1/answers")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ScoringReviewController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ScoringReviewService scoringReviewService;

    public ScoringReviewController(ScoringReviewService scoringReviewService) {
        this.scoringReviewService = scoringReviewService;
    }

    @GetMapping
    public ApiResponse<AnswerListResponse> list(
            @RequestParam(required = false) UUID sessionPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageRequest.of(boundedPage(page), boundedSize(size));
        return ApiResponse.success(
                scoringReviewService.listAnswers(sessionPublicId, status, pageable, currentUser()));
    }

    @GetMapping("/{answerPublicId}")
    public ApiResponse<AnswerReviewDetailResponse> get(@PathVariable UUID answerPublicId) {
        return ApiResponse.success(scoringReviewService.getAnswerForReview(answerPublicId, currentUser()));
    }

    @PostMapping("/{answerPublicId}/teacher-score")
    public ApiResponse<ScoringAnswerResponse> submitTeacherScore(@PathVariable UUID answerPublicId,
            @Valid @RequestBody SubmitTeacherScoreRequest request) {
        return ApiResponse.success(
                scoringReviewService.submitTeacherScore(answerPublicId, request.score(), currentUser()));
    }

    private int boundedPage(Integer requested) {
        return requested == null || requested < 0 ? 0 : requested;
    }

    private int boundedSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
