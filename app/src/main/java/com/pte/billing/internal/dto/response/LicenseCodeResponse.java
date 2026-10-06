package com.pte.billing.internal.dto.response;

import com.pte.billing.domain.LicenseCode;

import java.time.Instant;
import java.util.UUID;

public record LicenseCodeResponse(
        UUID publicId,
        String code,
        UUID planId,
        String status,
        UUID issuedBy,
        Instant issuedAt,
        Instant codeExpiresAt,
        UUID redeemedByTenantId,
        Instant redeemedAt,
        UUID subscriptionId,
        String revokeReason) {

    public static LicenseCodeResponse from(LicenseCode licenseCode) {
        return from(licenseCode, licenseCode.getStatus());
    }

    public static LicenseCodeResponse from(LicenseCode licenseCode,
            com.pte.billing.domain.enums.LicenseCodeStatus effectiveStatus) {
        return new LicenseCodeResponse(
                licenseCode.getPublicId(),
                licenseCode.getCode(),
                licenseCode.getPlanId(),
                effectiveStatus.name(),
                licenseCode.getIssuedBy(),
                licenseCode.getIssuedAt(),
                licenseCode.getCodeExpiresAt(),
                licenseCode.getRedeemedByTenantId(),
                licenseCode.getRedeemedAt(),
                licenseCode.getSubscriptionId(),
                licenseCode.getRevokeReason());
    }
}
