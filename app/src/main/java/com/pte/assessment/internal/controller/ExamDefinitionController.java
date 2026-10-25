package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.request.CreateExamDefinitionRequest;
import com.pte.assessment.dto.request.RejectExamDefinitionRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.service.ExamDefinitionAdminService;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.ExamDefinitionService;
import com.pte.itembank.TaskTypeRolloutProperties;
import com.pte.assessment.internal.exception.CustomTemplateActivationDisabledException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import com.pte.assessment.dto.request.ExamDefinitionStatusAction;
import com.pte.assessment.dto.request.ExamDefinitionStatusRequest;
import com.pte.shared.exception.StatusReasonRequiredException;
import com.pte.shared.security.SecurityRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Platform-owned score-template endpoints. Authors can work on drafts and
 * submit them for review; admins approve, reject, and activate; hosts only
 * receive the active template through the dedicated read endpoint.
 */
@RestController
@RequestMapping("/api/v1/exam-definitions")
public class ExamDefinitionController {

    private final ExamDefinitionAdminService adminService;
    private final ExamDefinitionService examDefinitionService;
    private final TaskTypeRolloutProperties rolloutProperties;

    @Autowired
    public ExamDefinitionController(ExamDefinitionAdminService adminService,
            ExamDefinitionService examDefinitionService, TaskTypeRolloutProperties rolloutProperties) {
        this.adminService = adminService;
        this.examDefinitionService = examDefinitionService;
        this.rolloutProperties = rolloutProperties;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<List<ExamDefinitionResponse>> list() {
        return ApiResponse.success(adminService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionResponse> create(
            @Valid @RequestBody CreateExamDefinitionRequest request) {
        return ApiResponse.success(adminService.createDraft(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.getForEdit(publicId));
    }

    @PostMapping("/{publicId}/clone")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.cloneToDraft(publicId, currentUser()));
    }

    @PutMapping("/{publicId}/items")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionResponse> replaceItems(@PathVariable UUID publicId,
                                                            @Valid @RequestBody ReplaceExamDefinitionItemsRequest request) {
        return ApiResponse.success(adminService.replaceItems(publicId, request, currentUser()));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<Void> delete(@PathVariable UUID publicId) {
        adminService.deleteDraft(publicId, currentUser());
        return ApiResponse.success(null);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<ExamDefinitionResponse> activeForHost() {
        if (examDefinitionService == null) {
            throw new IllegalStateException(ExamDefinitionConstants.TEMPLATE_READ_SERVICE_NOT_CONFIGURED);
        }
        return ApiResponse.success(examDefinitionService.getActive());
    }

    @PatchMapping("/{publicId}/status")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionResponse> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody ExamDefinitionStatusRequest request) {
        CurrentUser caller = currentUser();
        if (request.action() != ExamDefinitionStatusAction.SUBMIT_APPROVAL) {
            caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
        }
        return ApiResponse.success(switch (request.action()) {
            case SUBMIT_APPROVAL -> adminService.submitApproval(publicId, caller);
            case APPROVE -> adminService.approve(publicId, caller);
            case REJECT -> adminService.reject(publicId, new RejectExamDefinitionRequest(
                    StatusReasonRequiredException.requireReason(request.reason(),
                            ExamDefinitionConstants.TEMPLATE_APPROVAL_REASON_REQUIRED)), caller);
            case ACTIVATE -> activate(publicId, caller);
        });
    }

    @GetMapping("/{publicId}/feasibility")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
    public ApiResponse<ExamDefinitionFeasibilityResponse> feasibility(@PathVariable UUID publicId) {
        return ApiResponse.success(examDefinitionService.getTemplateFeasibility(publicId));
    }

    private ExamDefinitionResponse activate(UUID publicId, CurrentUser caller) {
        if (!rolloutProperties.isCustomTemplateActivationEnabled() && adminService.isCustomTemplate(publicId)) {
            throw new CustomTemplateActivationDisabledException();
        }
        return adminService.activate(publicId, caller);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
