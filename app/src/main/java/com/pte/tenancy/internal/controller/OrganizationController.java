package com.pte.tenancy.internal.controller;

import com.pte.tenancy.internal.dto.request.CreateOrganizationRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.service.OrganizationService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
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
 * Branch/facility endpoints, nested under a Tenant. Platform-admin only,
 * same as {@link TenantController}.
 */
@RestController
@RequestMapping("/tenants/{tenantPublicId}/organizations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ApiResponse<OrganizationResponse> create(@PathVariable UUID tenantPublicId,
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ApiResponse.success(organizationService.create(tenantPublicId, request, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<OrganizationResponse>> list(@PathVariable UUID tenantPublicId) {
        return ApiResponse.success(organizationService.list(tenantPublicId, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<OrganizationResponse> get(@PathVariable UUID tenantPublicId, @PathVariable UUID publicId) {
        return ApiResponse.success(organizationService.get(tenantPublicId, publicId, currentUser()));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<OrganizationResponse> suspend(@PathVariable UUID tenantPublicId, @PathVariable UUID publicId) {
        return ApiResponse.success(organizationService.suspend(tenantPublicId, publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reactivate")
    public ApiResponse<OrganizationResponse> reactivate(@PathVariable UUID tenantPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(organizationService.reactivate(tenantPublicId, publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
