package com.pte.billing.internal.dto.request.application;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A platform operator creates an organization directly. There is no plan or student limit here: the limit comes
 * from platform settings and packages are granted separately. {@code hostEmail} receives the first host account.
 */
public record DirectOnboardRequest(
        @NotBlank(message = BillingConstants.REQUESTED_CODE_REQUIRED)
        @Pattern(regexp = "^[a-z0-9-]{3,32}$", message = BillingConstants.REQUESTED_CODE_INVALID)
        String code,

        @NotBlank(message = BillingConstants.ORG_NAME_REQUIRED)
        @Size(max = 255, message = BillingConstants.ORG_NAME_MAX)
        String name,

        @NotBlank(message = BillingConstants.ORG_TYPE_REQUIRED)
        @Size(max = 255, message = BillingConstants.ORG_TYPE_MAX)
        String organizationType,

        @NotBlank(message = BillingConstants.TAX_CODE_REQUIRED)
        @Size(max = 64, message = BillingConstants.TAX_CODE_MAX)
        String taxCode,

        @NotBlank(message = BillingConstants.CONTACT_EMAIL_REQUIRED)
        @Email(message = BillingConstants.CONTACT_EMAIL_INVALID)
        @Size(max = 255, message = BillingConstants.CONTACT_EMAIL_MAX)
        String hostEmail) {
}
