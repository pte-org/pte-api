package com.pte.admin.controller;

import com.pte.admin.dto.request.GrantQuotaRequest;
import com.pte.admin.dto.request.OnboardTenantRequest;
import com.pte.admin.dto.request.UpdateBrandingRequest;
import com.pte.admin.dto.response.QuotaTransactionResponse;
import com.pte.admin.dto.response.TenantResponse;
import com.pte.admin.service.QuotaTransactionService;
import com.pte.admin.service.TenantLifecycleService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
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
    private final QuotaTransactionService quotaTransactionService;

    public TenantController(TenantLifecycleService tenantLifecycleService,
            QuotaTransactionService quotaTransactionService) {
        this.tenantLifecycleService = tenantLifecycleService;
        this.quotaTransactionService = quotaTransactionService;
    }

    @PostMapping
    public ApiResponse<TenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        return ApiResponse.success(tenantLifecycleService.onboard(request));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<TenantResponse> suspend(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.suspend(publicId));
    }

    @PostMapping("/{publicId}/reactivate")
    public ApiResponse<TenantResponse> reactivate(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.reactivate(publicId));
    }

    @PostMapping("/{publicId}/branding")
    public ApiResponse<TenantResponse> updateBranding(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateBrandingRequest request) {
        return ApiResponse.success(tenantLifecycleService.updateBranding(publicId, request));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<TenantResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(tenantLifecycleService.get(publicId));
    }

    @GetMapping
    public ApiResponse<List<TenantResponse>> list() {
        return ApiResponse.success(tenantLifecycleService.list());
    }

    @PostMapping("/{publicId}/quota-transactions")
    public ApiResponse<QuotaTransactionResponse> grantQuota(@PathVariable UUID publicId,
            @Valid @RequestBody GrantQuotaRequest request) {
        return ApiResponse.success(quotaTransactionService.grant(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}/quota-transactions")
    public ApiResponse<List<QuotaTransactionResponse>> quotaHistory(@PathVariable UUID publicId) {
        return ApiResponse.success(quotaTransactionService.history(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
