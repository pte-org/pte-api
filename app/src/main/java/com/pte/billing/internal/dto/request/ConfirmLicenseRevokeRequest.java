package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Explicit confirmation of one previously previewed revoke scope. */
public record ConfirmLicenseRevokeRequest(
        @NotBlank(message = BillingConstants.LICENSE_CODE_REVOKE_REASON_REQUIRED)
        @Size(max = 255, message = BillingConstants.LICENSE_CODE_REVOKE_REASON_MAX)
        String reason,
        @NotBlank(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        String scopeDigest,
        @NotNull(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        Instant previewExpiresAt,
        @NotBlank(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        String expectedEffectiveState,
        @NotNull(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        UUID expectedPlanId,
        UUID expectedSubscriptionPublicId,
        String expectedSubscriptionStatus,
        @NotNull(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        Boolean cancelSubscription,
        @NotNull(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        Boolean cancelScheduledScope,
        @NotNull(message = BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED)
        Boolean preserveOpenClosed) {
}
