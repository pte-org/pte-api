package com.pte.scoretemplate.internal.controller;

import com.pte.scoretemplate.dto.request.CreateScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.RejectScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.service.ScoreTemplateAdminService;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/score-templates")
public class ScoreTemplateController {

    private final ScoreTemplateAdminService adminService;
    private final ScoreTemplateService scoreTemplateService;

    @Autowired
    public ScoreTemplateController(ScoreTemplateAdminService adminService,
            ScoreTemplateService scoreTemplateService) {
        this.adminService = adminService;
        this.scoreTemplateService = scoreTemplateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<List<ScoreTemplateResponse>> list() {
        return ApiResponse.success(adminService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateResponse> create(
            @Valid @RequestBody CreateScoreTemplateRequest request) {
        return ApiResponse.success(adminService.createDraft(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.getForEdit(publicId));
    }

    @PostMapping("/{publicId}/clone")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.cloneToDraft(publicId, currentUser()));
    }

    @PutMapping("/{publicId}/items")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateResponse> replaceItems(@PathVariable UUID publicId,
                                                            @Valid @RequestBody ReplaceScoreTemplateItemsRequest request) {
        return ApiResponse.success(adminService.replaceItems(publicId, request, currentUser()));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<Void> delete(@PathVariable UUID publicId) {
        adminService.deleteDraft(publicId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{publicId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<ScoreTemplateResponse> activate(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.activate(publicId, currentUser()));
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<ScoreTemplateResponse> activeForHost() {
        if (scoreTemplateService == null) {
            throw new IllegalStateException("Score template read service is not configured");
        }
        return ApiResponse.success(scoreTemplateService.getActive());
    }

    @PostMapping("/{publicId}/submit-approval")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateResponse> submitApproval(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.submitApproval(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<ScoreTemplateResponse> approve(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.approve(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<ScoreTemplateResponse> reject(@PathVariable UUID publicId,
            @Valid @RequestBody RejectScoreTemplateRequest request) {
        return ApiResponse.success(adminService.reject(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}/feasibility")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<ScoreTemplateFeasibilityResponse> feasibility(@PathVariable UUID publicId) {
        return ApiResponse.success(scoreTemplateService.getTemplateFeasibility(publicId));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
