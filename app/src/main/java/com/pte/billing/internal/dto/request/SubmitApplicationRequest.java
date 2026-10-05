package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Submitted publicly, no auth — see BillingConstants for why validation here is strict. */
public record SubmitApplicationRequest(
        @NotBlank(message = BillingConstants.ORG_NAME_REQUIRED)
        @Size(max = 255, message = BillingConstants.ORG_NAME_MAX)
        String orgName,

        @NotBlank(message = BillingConstants.ORG_TYPE_REQUIRED)
        @Size(max = 255, message = BillingConstants.ORG_TYPE_MAX)
        String orgType,

        @NotBlank(message = BillingConstants.REQUESTED_CODE_REQUIRED)
        @Pattern(regexp = "^[a-z0-9-]{3,32}$", message = BillingConstants.REQUESTED_CODE_INVALID)
        String requestedCode,

        @NotBlank(message = BillingConstants.CONTACT_EMAIL_REQUIRED)
        @Email(message = BillingConstants.CONTACT_EMAIL_INVALID)
        @Size(max = 255, message = BillingConstants.CONTACT_EMAIL_MAX)
        String contactEmail,

        @Size(max = 255, message = BillingConstants.CONTACT_PHONE_MAX)
        String contactPhone,

        @NotBlank(message = BillingConstants.TAX_CODE_REQUIRED)
        @Size(max = 64, message = BillingConstants.TAX_CODE_MAX)
        String taxCode) {
}
