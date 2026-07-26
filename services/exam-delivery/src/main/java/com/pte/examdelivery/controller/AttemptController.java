package com.pte.examdelivery.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.service.AttemptService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Student-only, critical path. Every method after {@link #start} operates
 * purely on this service's own pinned data — no outbound call occurs.
 */
@RestController
@RequestMapping("/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class AttemptController {

    private final AttemptService attemptService;

    public AttemptController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping
    public ApiResponse<AttemptTaskResponse> start(@Valid @RequestBody StartAttemptRequest request) {
        return ApiResponse.success(attemptService.startAttempt(request, currentUser()));
    }

    @GetMapping("/{publicId}/next-task")
    public ApiResponse<AttemptTaskResponse> nextTask(@PathVariable UUID publicId) {
        return ApiResponse.success(attemptService.getNextTask(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/answers")
    public ApiResponse<AttemptTaskResponse> submitAnswer(@PathVariable UUID publicId,
                                                          @Valid @RequestBody SubmitAnswerRequest request) {
        return ApiResponse.success(attemptService.submitAnswer(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/submit")
    public ApiResponse<AttemptTaskResponse> submit(@PathVariable UUID publicId) {
        return ApiResponse.success(attemptService.submitAttempt(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
