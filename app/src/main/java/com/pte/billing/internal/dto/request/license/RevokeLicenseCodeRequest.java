package com.pte.billing.internal.dto.request.license;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeLicenseCodeRequest(
        @NotBlank(message = BillingConstants.LICENSE_CODE_REVOKE_REASON_REQUIRED)
        @Size(max = 255, message = BillingConstants.LICENSE_CODE_REVOKE_REASON_MAX)
        String reason) {
}
