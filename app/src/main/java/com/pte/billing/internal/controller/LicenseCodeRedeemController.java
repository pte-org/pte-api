package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.RedeemLicenseCodeRequest;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.service.LicenseCodeService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Host-admin endpoint for redeeming one activation code. */
@RestController
@RequestMapping("/api/license-codes")
public class LicenseCodeRedeemController {

    private final LicenseCodeService licenseCodeService;

    public LicenseCodeRedeemController(LicenseCodeService licenseCodeService) {
        this.licenseCodeService = licenseCodeService;
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<SubscriptionActivationResponse> redeem(
            @Valid @RequestBody RedeemLicenseCodeRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(licenseCodeService.redeem(request.code(), caller));
    }
}
