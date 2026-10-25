package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.request.CreateBlueprintRequest;
import com.pte.assessment.dto.request.RejectBlueprintRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.service.BlueprintService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import com.pte.assessment.dto.request.BlueprintStatusAction;
import com.pte.assessment.dto.request.BlueprintStatusRequest;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.StatusReasonRequiredException;
import com.pte.shared.security.SecurityRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blueprints")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class BlueprintController {

    private final BlueprintService blueprintService;

    public BlueprintController(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @PostMapping
    public ApiResponse<BlueprintResponse> create(@Valid @RequestBody CreateBlueprintRequest request) {
        return ApiResponse.success(blueprintService.create(request, currentUser()));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<BlueprintResponse> update(@PathVariable UUID publicId,
            @Valid @RequestBody CreateBlueprintRequest request) {
        return ApiResponse.success(blueprintService.update(publicId, request, currentUser()));
    }

    @PatchMapping("/{publicId}/status")
    public ApiResponse<?> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody BlueprintStatusRequest request) {
        CurrentUser caller = currentUser();
        if (request.action() != BlueprintStatusAction.SUBMIT_APPROVAL) {
            caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
        }
        return switch (request.action()) {
            case SUBMIT_APPROVAL -> ApiResponse.success(blueprintService.submitApproval(publicId, caller));
            case APPROVE -> ApiResponse.success(blueprintService.approve(publicId, caller));
            case REJECT -> ApiResponse.success(blueprintService.reject(publicId, new RejectBlueprintRequest(
                    StatusReasonRequiredException.requireReason(request.reason(),
                            AssessmentConstants.BLUEPRINT_REJECTION_REASON_REQUIRED)), caller));
        };
    }

    @GetMapping("/{publicId}")
    public ApiResponse<BlueprintResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(blueprintService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<BlueprintResponse>> list() {
        return ApiResponse.success(blueprintService.list(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
