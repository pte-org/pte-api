package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import java.time.Instant;

/** Reads effective expiry without depending on the materialization scheduler. */
public final class LicenseCodeStateResolver {
    private LicenseCodeStateResolver() {}

    public static LicenseCodeStatus resolve(LicenseCode code, Instant now) {
        if (code.getStatus() == LicenseCodeStatus.ISSUED && code.getCodeExpiresAt() != null
                && !code.getCodeExpiresAt().isAfter(now)) {
            return LicenseCodeStatus.EXPIRED;
        }
        return code.getStatus();
    }
}
