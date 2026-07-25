package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.request.CreateSessionRequest;
import com.pte.scheduling.dto.request.SetCompositionRequest;
import com.pte.scheduling.dto.response.SessionResponse;
import com.pte.scheduling.service.CompositionService;
import com.pte.scheduling.service.HostCommandService;
import com.pte.scheduling.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class SessionController {

    private final SessionService sessionService;
    private final CompositionService compositionService;
    private final HostCommandService hostCommandService;

    public SessionController(SessionService sessionService, CompositionService compositionService,
                             HostCommandService hostCommandService) {
        this.sessionService = sessionService;
        this.compositionService = compositionService;
        this.hostCommandService = hostCommandService;
    }

    @PostMapping
    public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.success(sessionService.create(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<SessionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> list() {
        return ApiResponse.success(sessionService.list(currentUser()));
    }

    @PutMapping("/{publicId}/composition")
    public ApiResponse<SessionResponse> setComposition(@PathVariable UUID publicId,
                                                        @Valid @RequestBody SetCompositionRequest request) {
        return ApiResponse.success(compositionService.setComposition(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/open")
    public ApiResponse<SessionResponse> open(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionService.open(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/close")
    public ApiResponse<SessionResponse> close(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionService.close(publicId, currentUser()));
    }

    /** Host command: trigger scoring for every submitted attempt in this session (host-gated, ADR-002). */
    @PostMapping("/{publicId}/score")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<Void> requestScoring(@PathVariable UUID publicId) {
        hostCommandService.requestScoring(publicId, currentUser());
        return ApiResponse.success(null);
    }

    /** Host command: publish scored results to students. */
    @PostMapping("/{publicId}/publish")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<Void> requestPublish(@PathVariable UUID publicId) {
        hostCommandService.requestPublish(publicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
