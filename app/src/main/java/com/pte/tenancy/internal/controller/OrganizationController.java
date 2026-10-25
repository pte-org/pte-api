package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.tenancy.TenantSuspensionImpact;
import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.request.UpdateOrganizationProfileRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.dto.response.QuotaGrantResponse;
import com.pte.tenancy.internal.service.OrganizationLifecycleService;
import com.pte.tenancy.internal.service.OrganizationService;
import com.pte.tenancy.internal.service.QuotaGrantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import com.pte.tenancy.internal.dto.request.OrganizationStatusAction;
import com.pte.tenancy.internal.dto.request.OrganizationStatusRequest;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.StatusReasonRequiredException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Organizations (tenants). Platform operators govern them (onboard, suspend, branding, quota); a Host reads and
 * edits only the organization in its token. Applications under review are served by billing's
 * {@code /applications} endpoints, not here.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private static final String PLATFORM_OPERATORS = "hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')";
    private static final String PLATFORM_OPERATORS_OR_HOST =
            "hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER','HOST_ADMIN')";

    private final OrganizationService organizationService;
    private final OrganizationLifecycleService lifecycleService;
    private final QuotaGrantService quotaGrantService;

    public OrganizationController(OrganizationService organizationService,
            OrganizationLifecycleService lifecycleService, QuotaGrantService quotaGrantService) {
        this.organizationService = organizationService;
        this.lifecycleService = lifecycleService;
        this.quotaGrantService = quotaGrantService;
    }

    @PostMapping
    @PreAuthorize(PLATFORM_OPERATORS)
    public ApiResponse<OrganizationResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        return ApiResponse.success(lifecycleService.onboard(request, currentUser()));
    }

    @GetMapping
    @PreAuthorize(PLATFORM_OPERATORS_OR_HOST)
    public ApiResponse<List<OrganizationResponse>> list() {
        return ApiResponse.success(organizationService.list(currentUser()));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize(PLATFORM_OPERATORS_OR_HOST)
    public ApiResponse<OrganizationResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(organizationService.get(publicId, currentUser()));
    }

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<OrganizationResponse> updateProfile(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateOrganizationProfileRequest request) {
        return ApiResponse.success(organizationService.updateProfile(publicId, request, currentUser()));
    }

    @PatchMapping("/{publicId}/status")
    @PreAuthorize(PLATFORM_OPERATORS)
    public ApiResponse<OrganizationResponse> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody OrganizationStatusRequest request) {
        CurrentUser caller = currentUser();
        if (request.action() == OrganizationStatusAction.REACTIVATE) {
            return ApiResponse.success(lifecycleService.reactivate(publicId, caller));
        }
        StatusReasonRequiredException.requireReason(request.impactDigest(),
                TenancyConstants.TENANT_SUSPENSION_IMPACT_DIGEST_REQUIRED);
        return ApiResponse.success(lifecycleService.suspendWithConfirmation(publicId, request.asSuspension(), caller));
    }

    @GetMapping("/{publicId}/suspension-impact")
    @PreAuthorize(PLATFORM_OPERATORS)
    public ApiResponse<TenantSuspensionImpact> suspensionImpact(@PathVariable UUID publicId,
            @RequestParam(required = false) Integer days) {
        return ApiResponse.success(lifecycleService.suspensionImpact(publicId, days));
    }

    @PostMapping("/{publicId}/branding")
    @PreAuthorize(PLATFORM_OPERATORS)
    public ApiResponse<OrganizationResponse> updateBranding(@PathVariable UUID publicId,
            @Valid @RequestBody UpdateBrandingRequest request) {
        return ApiResponse.success(lifecycleService.updateBranding(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/quota-grants")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<QuotaGrantResponse> grantQuota(@PathVariable UUID publicId,
            @Valid @RequestBody GrantQuotaRequest request) {
        return ApiResponse.success(quotaGrantService.grant(publicId, request, currentUser()));
    }

    @GetMapping("/{publicId}/quota-grants")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<QuotaGrantResponse>> quotaHistory(@PathVariable UUID publicId) {
        return ApiResponse.success(quotaGrantService.history(publicId));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
