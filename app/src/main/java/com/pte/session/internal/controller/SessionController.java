package com.pte.session.internal.controller;

import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.AddAudienceSourcesRequest;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.request.PatchExamScheduleRequest;
import com.pte.session.internal.dto.response.audience.AudiencePreviewResponse;
import com.pte.session.internal.dto.response.audience.AudienceSourceResponse;
import com.pte.session.internal.dto.response.ExamPreflightResponse;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.dto.response.GenerationJobResponse;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.draft.LegacySessionCreateRequiresNewWorkflowException;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.audience.ExamAudienceService;
import com.pte.session.internal.service.draft.ExamDraftService;
import com.pte.session.internal.service.draft.ExamPublishService;
import com.pte.session.internal.service.draft.SessionExamPreviewService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import com.pte.session.internal.dto.request.SessionStatusRequest;
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
    private final ExamDraftService draftService;
    private final ExamAudienceService audienceService;
    private final ExamPublishService publishService;
    private final SessionExamPreviewService sessionExamPreviewService;

    public SessionController(SessionLifecycleService sessionLifecycleService,
            ExamDraftService draftService, ExamAudienceService audienceService, ExamPublishService publishService,
            SessionExamPreviewService sessionExamPreviewService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.draftService = draftService;
        this.audienceService = audienceService;
        this.publishService = publishService;
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
        return ApiResponse.success(draftService.createDraft(request, currentUser()));
    }

    @PatchMapping("/{publicId}")
    public ApiResponse<SessionResponse> updateDraft(@PathVariable UUID publicId,
            @Valid @RequestBody PatchExamDraftRequest request) {
        return ApiResponse.success(draftService.updateDraft(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/audience-sources")
    public ApiResponse<AudienceSourceResponse> addAudienceSource(@PathVariable UUID publicId,
            @Valid @RequestBody AudienceSourceRequest request) {
        return ApiResponse.success(audienceService.addSource(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/audience-sources/bulk")
    public ApiResponse<List<AudienceSourceResponse>> addAudienceSources(@PathVariable UUID publicId,
            @Valid @RequestBody AddAudienceSourcesRequest request) {
        return ApiResponse.success(audienceService.addSources(publicId, request.sources(), currentUser()));
    }

    @GetMapping("/{publicId}/audience-sources")
    public ApiResponse<List<AudienceSourceResponse>> listAudienceSources(@PathVariable UUID publicId) {
        return ApiResponse.success(audienceService.listSources(publicId, currentUser()));
    }

    @DeleteMapping("/{publicId}/audience-sources/{sourcePublicId}")
    public ApiResponse<Void> removeAudienceSource(@PathVariable UUID publicId, @PathVariable UUID sourcePublicId) {
        audienceService.removeSource(publicId, sourcePublicId, currentUser());
        return ApiResponse.success(null);
    }

    @PostMapping("/{publicId}/audience-preview")
    public ApiResponse<AudiencePreviewResponse> previewAudience(@PathVariable UUID publicId) {
        return ApiResponse.success(audienceService.previewAudience(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/preflight")
    public ApiResponse<ExamPreflightResponse> preflight(@PathVariable UUID publicId) {
        return ApiResponse.success(publishService.preflight(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/generate")
    public ApiResponse<GenerationJobResponse> generate(@PathVariable UUID publicId,
            @RequestParam String idempotencyKey) {
        return ApiResponse.success(publishService.generate(publicId, idempotencyKey, currentUser()));
    }

    @GetMapping("/{publicId}/generation")
    public ApiResponse<GenerationJobResponse> generation(@PathVariable UUID publicId) {
        return ApiResponse.success(publishService.generation(publicId, currentUser()));
    }

    @PatchMapping("/{publicId}/status")
    public ApiResponse<SessionResponse> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody SessionStatusRequest request) {
        CurrentUser caller = currentUser();
        return ApiResponse.success(switch (request.action()) {
            case PUBLISH -> publishService.publish(publicId, caller);
            case CANCEL -> publishService.cancel(publicId, caller);
            case OPEN -> sessionLifecycleService.open(publicId, caller);
            case CLOSE -> sessionLifecycleService.close(publicId, caller);
        });
    }

    @DeleteMapping("/{publicId}")
    public ApiResponse<Void> deleteDraft(@PathVariable UUID publicId) {
        draftService.deleteDraft(publicId, currentUser());
        return ApiResponse.success(null);
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

    @PatchMapping("/{publicId}/schedule")
    public ApiResponse<SessionResponse> patchSchedule(@PathVariable UUID publicId,
            @Valid @RequestBody PatchExamScheduleRequest request) {
        return ApiResponse.success(sessionLifecycleService.patchSchedule(publicId, request, currentUser()));
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
