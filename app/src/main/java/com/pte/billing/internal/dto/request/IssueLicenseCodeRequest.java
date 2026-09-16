package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record IssueLicenseCodeRequest(
        @NotNull(message = BillingConstants.LICENSE_CODE_PLAN_REQUIRED)
        UUID planId,

        @Future(message = BillingConstants.LICENSE_CODE_EXPIRY_INVALID)
        Instant codeExpiresAt) {
}
