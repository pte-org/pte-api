package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;

public record RedeemLicenseCodeRequest(
        @NotBlank(message = BillingConstants.LICENSE_CODE_REQUIRED)
        String code) {
}
