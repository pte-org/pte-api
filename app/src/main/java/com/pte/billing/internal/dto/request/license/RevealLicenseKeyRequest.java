package com.pte.billing.internal.dto.request.license;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;

public record RevealLicenseKeyRequest(
        @NotBlank(message = BillingConstants.PASSWORD_REQUIRED)
        String password) {
}
