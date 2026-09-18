package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.request.CreateBlueprintRequest;
import com.pte.assessment.dto.request.RejectBlueprintRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.BlueprintService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
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

    @PostMapping("/{publicId}/submit-approval")
    public ApiResponse<BlueprintResponse> submitApproval(@PathVariable UUID publicId) {
        return ApiResponse.success(blueprintService.submitApproval(publicId, currentUser()));
    }

    @PostMapping({"/{publicId}/approval", "/{publicId}/approve"})
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<SnapshotResponse> approve(@PathVariable UUID publicId) {
        return ApiResponse.success(blueprintService.approve(publicId, currentUser()));
    }

    @PostMapping({"/{publicId}/rejection", "/{publicId}/reject"})
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<BlueprintResponse> reject(@PathVariable UUID publicId,
            @Valid @RequestBody RejectBlueprintRequest request) {
        return ApiResponse.success(blueprintService.reject(publicId, request, currentUser()));
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
