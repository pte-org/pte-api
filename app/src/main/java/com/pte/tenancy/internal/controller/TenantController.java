package com.pte.tenancy.internal.controller;

import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.response.QuotaTransactionResponse;
import com.pte.tenancy.internal.dto.response.TenantResponse;
import com.pte.tenancy.internal.service.QuotaTransactionService;
import com.pte.tenancy.internal.service.TenantLifecycleService;
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
 * Tenant governance endpoints. Platform-scoped operations roles may use the
 * operational actions; sensitive quota operations remain admin-only.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class TenantController {

    private final TenantLifecycleService tenantLifecycleService;
    private final QuotaTransactionService quotaTransactionService;

    public TenantController(TenantLifecycleService tenantLifecycleService,
            QuotaTransactionService quotaTransactionService) {
        this.tenantLifecycleService = tenantLifecycleService;
        this.quotaTransactionService = quotaTransactionService;
    }

    @PostMapping
    public ApiResponse<TenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        return ApiResponse.success(tenantLifecycleService.onboard(request, currentUser()));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<TenantResponse> suspend(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.suspend(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reactivate")
    public ApiResponse<TenantResponse> reactivate(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.reactivate(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/branding")
    public ApiResponse<TenantResponse> updateBranding(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateBrandingRequest request) {
        return ApiResponse.success(tenantLifecycleService.updateBranding(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<TenantResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list() {
        return ApiResponse.success(tenantLifecycleService.list(currentUser()));
    }

    @PostMapping("/{publicId}/quota-transactions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<QuotaTransactionResponse> grantQuota(@PathVariable UUID publicId,
            @Valid @RequestBody GrantQuotaRequest request) {
        return ApiResponse.success(quotaTransactionService.grant(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}/quota-transactions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<QuotaTransactionResponse>> quotaHistory(@PathVariable UUID publicId) {
        return ApiResponse.success(quotaTransactionService.history(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
