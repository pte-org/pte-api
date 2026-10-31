package com.pte.billing.internal.dto.request.license;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Admin grant of one plan to one organization under a contract; a null start means the default continuation. */
public record GrantLicenseRequest(
        @NotNull(message = BillingConstants.LICENSE_GRANT_TENANT_REQUIRED)
        UUID tenantId,

        @NotNull(message = BillingConstants.LICENSE_CODE_PLAN_REQUIRED)
        UUID planId,

        Instant startsAt,

        @NotBlank(message = BillingConstants.LICENSE_GRANT_CONTRACT_REQUIRED)
        @Size(max = 64, message = BillingConstants.LICENSE_GRANT_CONTRACT_TOO_LONG)
        String contractReference,

        @Size(max = 500, message = BillingConstants.LICENSE_GRANT_NOTE_TOO_LONG)
        String note) {
}
