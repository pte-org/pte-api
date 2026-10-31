package com.pte.billing.internal.dto.request.license;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only preview of a grant; a null start previews the default continuation. A null plan returns only the
 * organization's hosts, for the confirmation step shown before a plan is chosen.
 */
public record PreviewLicenseGrantRequest(
        @NotNull(message = BillingConstants.LICENSE_GRANT_TENANT_REQUIRED)
        UUID tenantId,

        UUID planId,

        Instant startsAt) {
}
