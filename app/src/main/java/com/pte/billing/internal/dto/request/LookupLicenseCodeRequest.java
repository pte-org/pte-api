package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;

/** Body-only lookup for an admin license bearer; the value must not enter a URL. */
public record LookupLicenseCodeRequest(
        @NotBlank(message = BillingConstants.LICENSE_CODE_REQUIRED) String code) {
}
