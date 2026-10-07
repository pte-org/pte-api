package com.pte.practice.internal.controller;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.service.PracticeSessionService;
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

    public PracticeSessionController(PracticeSessionService sessionService) {
        this.sessionService = sessionService;
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
}
