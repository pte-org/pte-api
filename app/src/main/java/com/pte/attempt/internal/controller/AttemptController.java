package com.pte.attempt.internal.controller;

import com.pte.attempt.internal.dto.request.EncryptedSubmissionRequest;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.request.SubmitAnswerRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.dto.response.AudioPlayResponse;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.service.AttemptLifecycleService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Student-only, critical path. Every method after {@link #start} operates
 * purely on this module's own pinned data — no call to another module occurs.
 */
@RestController
@RequestMapping("/api/v1/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class AttemptController {

    private final AttemptLifecycleService attemptLifecycleService;

    public AttemptController(AttemptLifecycleService attemptLifecycleService) {
        this.attemptLifecycleService = attemptLifecycleService;
    }

    @PostMapping
    public ApiResponse<AttemptTaskResponse> start(@Valid @RequestBody StartAttemptRequest request) {
        return ApiResponse.success(attemptLifecycleService.startAttempt(request, currentUser()));
    }

    @PostMapping("/preflight")
    public ApiResponse<AttemptPreflightResponse> preflight(@Valid @RequestBody AttemptPreflightRequest request) {
        return ApiResponse.success(attemptLifecycleService.preflight(request, currentUser()));
    }

    @GetMapping("/{publicId}/next-task")
    public ApiResponse<AttemptTaskResponse> nextTask(@PathVariable UUID publicId) {
        return ApiResponse.success(attemptLifecycleService.getNextTask(publicId, currentUser()));
    }

    /** STANDARD-pinned attempts only — server rejects if the attempt is pinned STRICT. */
    @PostMapping("/{publicId}/answers")
    public ApiResponse<AttemptTaskResponse> submitAnswer(@PathVariable UUID publicId,
                                                          @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.success(attemptLifecycleService.submitAnswer(publicId, request, currentUser()));
    }

    /** STRICT-pinned attempts only — server rejects if the attempt is pinned STANDARD. */
    @PostMapping("/{publicId}/answers/encrypted")
    public ApiResponse<AttemptTaskResponse> submitEncryptedAnswer(@PathVariable UUID publicId,
                                                                   @Valid @RequestBody EncryptedSubmissionRequest request) {
        return ApiResponse.success(attemptLifecycleService.submitEncryptedAnswer(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/submit")
    public ApiResponse<AttemptTaskResponse> submit(@PathVariable UUID publicId) {
        return ApiResponse.success(attemptLifecycleService.submitAttempt(publicId, currentUser()));
    }

    /** {@code X-Play-Request-Id} is a client-generated UUID per user-initiated play tap — required for idempotent retry-safety. */
    @GetMapping("/{publicId}/items/{itemPublicId}/audio")
    public ApiResponse<AudioPlayResponse> playAudio(@PathVariable UUID publicId, @PathVariable UUID itemPublicId,
                                                     @RequestHeader("X-Play-Request-Id") String playRequestId) {
        return ApiResponse.success(attemptLifecycleService.playAudio(publicId, itemPublicId, playRequestId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
