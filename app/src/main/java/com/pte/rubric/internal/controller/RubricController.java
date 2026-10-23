package com.pte.rubric.internal.controller;

import com.pte.rubric.dto.request.CreateRubricRequest;
import com.pte.rubric.dto.request.PublishRubricRequest;
import com.pte.rubric.dto.request.RejectRubricRequest;
import com.pte.rubric.dto.request.ReplaceRubricDocumentRequest;
import com.pte.rubric.dto.response.RubricResponse;
import com.pte.rubric.dto.response.RubricDiffResponse;
import com.pte.rubric.dto.response.RubricReadinessResponse;
import com.pte.rubric.internal.service.RubricDraftService;
import com.pte.rubric.internal.service.RubricLifecycleService;
import com.pte.rubric.internal.service.RubricReadService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import com.pte.shared.web.PagedResult;

@RestController
@RequestMapping("/api/v1/practice-rubrics")
public class RubricController {

    private final RubricReadService readService;
    private final RubricDraftService draftService;
    private final RubricLifecycleService lifecycleService;

    public RubricController(RubricReadService readService, RubricDraftService draftService,
            RubricLifecycleService lifecycleService) {
        this.readService = readService;
        this.draftService = draftService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<PagedResult<RubricResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskTypeKey,
            @RequestParam(required = false) String rubricKey,
            @RequestParam(required = false) UUID authorUserPublicId,
            @RequestParam(required = false) Integer version) {
        return ApiResponse.success(readService.list(page, size, status, taskTypeKey, rubricKey,
                authorUserPublicId, version, currentUser()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricResponse> create(@Valid @RequestBody CreateRubricRequest request) {
        return ApiResponse.success(draftService.createDraft(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(readService.get(publicId, currentUser()));
    }

    @GetMapping("/{publicId}/diff/{otherPublicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricDiffResponse> diff(@PathVariable UUID publicId,
            @PathVariable UUID otherPublicId) {
        return ApiResponse.success(readService.diff(publicId, otherPublicId, currentUser()));
    }

    @PostMapping("/{publicId}/clone")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(draftService.cloneToDraft(publicId, currentUser()));
    }

    @PutMapping("/{publicId}/document")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricResponse> replaceDocument(@PathVariable UUID publicId,
            @Valid @RequestBody ReplaceRubricDocumentRequest request) {
        return ApiResponse.success(draftService.replaceDocument(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/submit-approval")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricResponse> submitApproval(@PathVariable UUID publicId) {
        return ApiResponse.success(draftService.submitApproval(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reject")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')")
    public ApiResponse<RubricResponse> reject(@PathVariable UUID publicId,
            @Valid @RequestBody RejectRubricRequest request) {
        return ApiResponse.success(lifecycleService.reject(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/publish")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')")
    public ApiResponse<RubricResponse> publish(@PathVariable UUID publicId,
            @Valid @RequestBody PublishRubricRequest request) {
        return ApiResponse.success(lifecycleService.publish(publicId, request, currentUser()));
    }

    /** Explicit platform-admin route so an override is visible in API policy and audit review. */
    @PostMapping("/{publicId}/override-publish")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<RubricResponse> overridePublish(@PathVariable UUID publicId,
            @Valid @RequestBody PublishRubricRequest request) {
        return ApiResponse.success(lifecycleService.publish(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/retire")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER')")
    public ApiResponse<RubricResponse> retire(@PathVariable UUID publicId,
            @RequestParam(required = false) Long expectedVersion) {
        return ApiResponse.success(lifecycleService.retire(publicId, expectedVersion, currentUser()));
    }

    @GetMapping("/{publicId}/readiness")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<RubricReadinessResponse> readiness(@PathVariable UUID publicId) {
        readService.requireReadable(publicId, currentUser());
        return ApiResponse.success(lifecycleService.readiness(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
