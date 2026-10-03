package com.pte.billing.internal.controller;

import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.internal.dto.request.RevealLicenseKeyRequest;
import com.pte.billing.internal.dto.response.LicenseKeyResponse;
import com.pte.billing.internal.dto.response.SubscriptionResponse;
import com.pte.billing.internal.exception.InvalidLicenseKeyRevealPasswordException;
import com.pte.billing.internal.exception.SubscriptionNotFoundException;
import com.pte.identity.IdentityService;
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

/** Tenant-owned subscription reads, including exact historical state for billing status views. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class SubscriptionController {

    private final BillingService billingService;
    private final IdentityService identityService;

    public SubscriptionController(BillingService billingService, IdentityService identityService) {
        this.billingService = billingService;
        this.identityService = identityService;
    }

    @GetMapping
    public ApiResponse<List<SubscriptionResponse>> list() {
        CurrentUser caller = CurrentUserContext.required();
        if (caller.tenantId() == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(billingService.listActiveSubscriptions(caller.tenantId()).stream()
                .map(SubscriptionResponse::from)
                .toList());
    }

    /** Exact tenant-owned lookup; inactive subscriptions remain addressable for billing history. */
    @GetMapping("/{publicId}")
    public ApiResponse<SubscriptionResponse> get(@PathVariable UUID publicId) {
        CurrentUser caller = CurrentUserContext.required();
        if (caller.tenantId() == null) {
            throw new SubscriptionNotFoundException();
        }
        return ApiResponse.success(billingService.getSubscription(publicId, caller.tenantId())
                .map(SubscriptionResponse::from)
                .orElseThrow(SubscriptionNotFoundException::new));
    }

    /**
     * Step-up re-authentication in front of a live credential: {@code list()} above
     * only ever returns the license key masked, so a Host who wants the full value
     * (e.g. to hand it to another admin) must re-enter their own password here first.
     */
    @PostMapping("/{publicId}/license-key/reveal")
    public ApiResponse<LicenseKeyResponse> revealLicenseKey(@PathVariable UUID publicId,
            @Valid @RequestBody RevealLicenseKeyRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        if (caller.tenantId() == null) {
            throw new SubscriptionNotFoundException();
        }
        if (!identityService.verifyPassword(caller.userId(), request.password())) {
            throw new InvalidLicenseKeyRevealPasswordException();
        }
        SubscriptionView subscription = billingService.getActiveSubscription(publicId, caller.tenantId())
                .orElseThrow(SubscriptionNotFoundException::new);
        return ApiResponse.success(new LicenseKeyResponse(subscription.licenseKey()));
    }
}
