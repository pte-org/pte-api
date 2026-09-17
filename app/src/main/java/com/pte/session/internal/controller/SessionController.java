package com.pte.session.internal.controller;

import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Host command endpoints {@code /score} and {@code /publish} (source:
 * {@code HostCommandService}) are deferred to Phase 08 (scoring) and Phase 10
 * (reporting) respectively — in the source microservice they only ever
 * published an outbox event for a downstream service that doesn't exist yet
 * in this monolith; porting them now would mean a command with no callable
 * target.
 */
@RestController
@RequestMapping("/sessions")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class SessionController {

    private final SessionLifecycleService sessionLifecycleService;

    public SessionController(SessionLifecycleService sessionLifecycleService) {
        this.sessionLifecycleService = sessionLifecycleService;
    }

    @PostMapping
    public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionLifecycleService.create(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<SessionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionLifecycleService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> list() {
        return ApiResponse.success(sessionLifecycleService.list(currentUser()));
    }

    /** Partial ExamPolicy override — rejected once the session is past pre-open, regardless of attempt count. */
    @PatchMapping("/{publicId}/policy")
    public ApiResponse<ExamPolicyResponse> patchPolicy(@PathVariable UUID publicId,
                                                        @Valid @RequestBody PatchExamPolicyRequest request) {
        return ApiResponse.success(sessionLifecycleService.patchPolicy(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/open")
    public ApiResponse<SessionResponse> open(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionLifecycleService.open(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/close")
    public ApiResponse<SessionResponse> close(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionLifecycleService.close(publicId, currentUser()));
    }

    @PatchMapping("/{publicId}/subscription")
    public ApiResponse<SessionResponse> changeSubscription(@PathVariable UUID publicId,
            @Valid @RequestBody ChangeSubscriptionRequest request) {
        return ApiResponse.success(sessionLifecycleService.changeSubscription(publicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
