package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeExplanationRequest;
import com.pte.practice.internal.dto.response.PracticeExplanationResponse;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.service.PracticeExplanationService;
import com.pte.practice.internal.service.PracticeReviewService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Student-owned explanation read/generate boundary. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_SESSION_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeExplanationController {

    private final PracticeExplanationService explanationService;
    private final PracticeReviewService reviewService;

    public PracticeExplanationController(PracticeExplanationService explanationService,
            PracticeReviewService reviewService) {
        this.explanationService = explanationService;
        this.reviewService = reviewService;
    }

    @GetMapping("/{sessionPublicId}/review")
    public ApiResponse<PracticeReviewResponse> review(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(reviewService.get(sessionPublicId, CurrentUserContext.required()));
    }

    @GetMapping("/{sessionPublicId}/items/{itemPublicId}/explanation")
    public ApiResponse<PracticeExplanationResponse> get(
            @PathVariable UUID sessionPublicId, @PathVariable UUID itemPublicId,
            @RequestParam(defaultValue = "vi") String language) {
        return ApiResponse.success(explanationService.get(sessionPublicId, itemPublicId, language,
                CurrentUserContext.required()));
    }

    @PostMapping("/{sessionPublicId}/items/{itemPublicId}/explanation")
    public ApiResponse<PracticeExplanationResponse> request(
            @PathVariable UUID sessionPublicId, @PathVariable UUID itemPublicId,
            @RequestHeader(name = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PracticeExplanationRequest request) {
        return ApiResponse.success(explanationService.request(sessionPublicId, itemPublicId, request.language(),
                idempotencyKey, CurrentUserContext.required()));
    }
}
