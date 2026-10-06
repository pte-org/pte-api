package com.pte.billing.internal.dto.request;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;
import com.pte.billing.internal.exception.LicenseCodeException;
import org.springframework.http.HttpStatus;

public record IssueLicenseCodeRequest(
        @NotNull(message = BillingConstants.LICENSE_CODE_PLAN_REQUIRED)
        UUID planId,

        Instant codeExpiresAt) {

    public static UUID parseIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED,
                    BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE);
        }
        try {
            UUID key = UUID.fromString(raw);
            if (!key.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException ex) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_INVALID,
                    BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_INVALID_MESSAGE);
        }
    }
}
