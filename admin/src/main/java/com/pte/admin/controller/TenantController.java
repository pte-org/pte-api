package com.pte.admin.controller;

import com.pte.admin.dto.request.OnboardTenantRequest;
import com.pte.admin.dto.response.TenantResponse;
import com.pte.admin.service.TenantLifecycleService;
import com.pte.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant governance endpoints. Platform-admin only; nothing on a data-plane
 * runtime path calls these.
 */
@RestController
@RequestMapping("/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantController {

    private final TenantLifecycleService tenantLifecycleService;

    public TenantController(TenantLifecycleService tenantLifecycleService) {
        this.tenantLifecycleService = tenantLifecycleService;
    }

    @PostMapping
    public ApiResponse<TenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        return ApiResponse.success(tenantLifecycleService.onboard(request));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<TenantResponse> suspend(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.suspend(publicId));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<TenantResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.get(publicId));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list() {
        return ApiResponse.success(tenantLifecycleService.list());
    }
}
