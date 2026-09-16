package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.service.PlanService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform-admin catalog management plus the authenticated active catalog. */
@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/plans")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PlanResponse>> listActive() {
        return ApiResponse.success(planService.listActive());
    }

    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.success(planService.create(request));
    }

    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<PlanResponse>> listForAdmin() {
        return ApiResponse.success(planService.listForAdmin());
    }

    @GetMapping("/admin/plans/{publicId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<PlanResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(planService.get(publicId));
    }

    @PutMapping("/admin/plans/{publicId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<PlanResponse> update(@PathVariable UUID publicId,
            @Valid @RequestBody PlanRequest request) {
        return ApiResponse.success(planService.update(publicId, request));
    }

    @PostMapping("/admin/plans/{publicId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<PlanResponse> activate(@PathVariable UUID publicId) {
        return ApiResponse.success(planService.activate(publicId));
    }

    @PostMapping("/admin/plans/{publicId}/archive")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<PlanResponse> archive(@PathVariable UUID publicId) {
        return ApiResponse.success(planService.archive(publicId));
    }
}
