package com.pte.session.internal.controller;

import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.AudiencePreviewResponse;
import com.pte.session.internal.dto.response.AudienceSourceResponse;
import com.pte.session.internal.dto.response.ExamPreflightResponse;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.dto.response.GenerationJobResponse;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.LegacySessionCreateRequiresNewWorkflowException;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.ExamOrchestrationService;
import com.pte.session.internal.service.SessionExamPreviewService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

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
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class SessionController {

    private final SessionLifecycleService sessionLifecycleService;
    private final ExamOrchestrationService examOrchestrationService;
    private final SessionExamPreviewService sessionExamPreviewService;

    public SessionController(SessionLifecycleService sessionLifecycleService,
            ExamOrchestrationService examOrchestrationService,
            SessionExamPreviewService sessionExamPreviewService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.examOrchestrationService = examOrchestrationService;
        this.sessionExamPreviewService = sessionExamPreviewService;
    }

    @PostMapping
    public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        // The old skills-only payload cannot express the template, audience,
        // form, and reuse rules required by the canonical workflow. Keep the
        // route during the migration window, but fail closed instead of
        // creating an unvalidated scheduled exam.
        throw new LegacySessionCreateRequiresNewWorkflowException();
    }

    @PostMapping("/drafts")
    public ApiResponse<SessionResponse> createDraft(@Valid @RequestBody CreateExamDraftRequest request) {
        return ApiResponse.success(examOrchestrationService.createDraft(request, currentUser()));
    }

    @PatchMapping("/{publicId}")
    public ApiResponse<SessionResponse> updateDraft(@PathVariable UUID publicId,
            @RequestBody PatchExamDraftRequest request) {
        return ApiResponse.success(examOrchestrationService.updateDraft(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/audience-sources")
    public ApiResponse<AudienceSourceResponse> addAudienceSource(@PathVariable UUID publicId,
            @Valid @RequestBody AudienceSourceRequest request) {
        return ApiResponse.success(examOrchestrationService.addSource(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}/audience-sources")
    public ApiResponse<List<AudienceSourceResponse>> listAudienceSources(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.listSources(publicId, currentUser()));
    }

    @DeleteMapping("/{publicId}/audience-sources/{sourcePublicId}")
    public ApiResponse<Void> removeAudienceSource(@PathVariable UUID publicId, @PathVariable UUID sourcePublicId) {
        examOrchestrationService.removeSource(publicId, sourcePublicId, currentUser());
        return ApiResponse.success(null);
    }

    @PostMapping("/{publicId}/audience-preview")
    public ApiResponse<AudiencePreviewResponse> previewAudience(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.previewAudience(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/preflight")
    public ApiResponse<ExamPreflightResponse> preflight(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.preflight(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/generate")
    public ApiResponse<GenerationJobResponse> generate(@PathVariable UUID publicId,
            @RequestParam String idempotencyKey) {
        return ApiResponse.success(examOrchestrationService.generate(publicId, idempotencyKey, currentUser()));
    }

    @GetMapping("/{publicId}/generation")
    public ApiResponse<GenerationJobResponse> generation(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.generation(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/publish")
    public ApiResponse<SessionResponse> publish(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.publish(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/cancel")
    public ApiResponse<SessionResponse> cancel(@PathVariable UUID publicId) {
        return ApiResponse.success(examOrchestrationService.cancel(publicId, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<SessionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionLifecycleService.get(publicId, currentUser()));
    }

    @GetMapping("/{publicId}/exam-preview")
    public ApiResponse<ExamPreviewResponse> examPreview(@PathVariable UUID publicId) {
        return ApiResponse.success(sessionExamPreviewService.preview(publicId, currentUser()));
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
