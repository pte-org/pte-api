package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeDraftRequest;
import com.pte.practice.internal.dto.request.PracticeSaveAndExitRequest;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.dto.response.media.PracticeRecordingPlaybackResponse;
import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.service.PracticeRecordingPlaybackService;
import com.pte.practice.internal.service.PracticeReviewService;
import com.pte.practice.internal.service.session.PracticeSessionService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Standalone practice overview/begin/heartbeat boundary. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_SESSION_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_WEB_ENABLED_PROPERTY, havingValue = "true")
public class PracticeSessionController {

    private final PracticeSessionService sessionService;
    private final PracticeReviewService reviewService;
    private final PracticeRecordingPlaybackService playbackService;

    public PracticeSessionController(PracticeSessionService sessionService, PracticeReviewService reviewService,
            PracticeRecordingPlaybackService playbackService) {
        this.sessionService = sessionService;
        this.reviewService = reviewService;
        this.playbackService = playbackService;
    }

    @PostMapping
    public ApiResponse<PracticeSessionResponse> start(
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeSessionStartRequest request) {
        return ApiResponse.success(sessionService.start(request, idempotencyKey, CurrentUserContext.required()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<PracticeSessionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionService.get(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/begin")
    public ApiResponse<PracticeSessionResponse> begin(
            @PathVariable UUID publicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeSessionActionRequest request) {
        return ApiResponse.success(sessionService.begin(publicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/heartbeat")
    public ApiResponse<PracticeSessionResponse> heartbeat(
            @PathVariable UUID publicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeSessionActionRequest request) {
        return ApiResponse.success(sessionService.heartbeat(publicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/items/{itemPublicId}/answer")
    public ApiResponse<PracticeSessionResponse> answer(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeAnswerRequest request) {
        return ApiResponse.success(sessionService.answer(publicId, itemPublicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }

    /** Poll target while an AI-scored answer is SCORING. */
    @GetMapping("/{publicId}/items/{itemPublicId}/result")
    public ApiResponse<PracticeItemResultResponse> result(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId) {
        return ApiResponse.success(sessionService.itemResult(publicId, itemPublicId,
                CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/items/{itemPublicId}/scoring/retry")
    public ApiResponse<PracticeItemResultResponse> retryScoring(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId) {
        return ApiResponse.success(sessionService.retryScoring(publicId, itemPublicId,
                CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/items/{itemPublicId}/skip")
    public ApiResponse<PracticeSessionResponse> skip(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeSessionActionRequest request) {
        return ApiResponse.success(sessionService.skip(publicId, itemPublicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }

    /**
     * Link to the student's own saved recording of an item, to replay it and draw its waveform after a reload. Only
     * reads the caller's own data, so it needs no entitlement check and works in any session state.
     */
    @GetMapping("/{publicId}/items/{itemPublicId}/recording")
    public ApiResponse<PracticeRecordingPlaybackResponse> recording(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId) {
        return ApiResponse.success(playbackService.playback(publicId, itemPublicId, CurrentUserContext.required()));
    }

    /** Saves the current item's work without leaving the session (a finished or re-done recording). */
    @PostMapping("/{publicId}/items/{itemPublicId}/draft")
    public ApiResponse<PracticeSessionResponse> saveDraft(
            @PathVariable UUID publicId,
            @PathVariable UUID itemPublicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeDraftRequest request) {
        return ApiResponse.success(sessionService.saveDraft(publicId, itemPublicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }

    @GetMapping("/{publicId}/review")
    public ApiResponse<PracticeReviewResponse> review(@PathVariable UUID publicId) {
        return ApiResponse.success(reviewService.get(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/save-and-exit")
    public ApiResponse<PracticeSessionResponse> saveAndExit(
            @PathVariable UUID publicId,
            @RequestHeader(value = PracticeConstants.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @Valid @RequestBody PracticeSaveAndExitRequest request) {
        return ApiResponse.success(sessionService.saveAndExit(publicId, request, idempotencyKey,
                CurrentUserContext.required()));
    }
}
