package com.pte.billing.internal.dto.response;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.tenancy.TenantSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe admin representation of a license code. The bearer itself is
 * deliberately not a component of this DTO; only a fixed mask and its last
 * four characters are exposed.
 */
public record AdminLicenseCodeSummary(
        UUID publicId,
        String maskedCode,
        String persistedStatus,
        String effectiveStatus,
        UUID planPublicId,
        String planName,
        String planType,
        Instant issuedAt,
        Instant codeExpiresAt,
        UUID recipientPublicId,
        String recipientName,
        UUID subscriptionPublicId) {

    public static AdminLicenseCodeSummary from(LicenseCode licenseCode,
            LicenseCodeStatus effectiveStatus, Plan plan, TenantSummary recipient) {
        boolean planAvailable = plan != null && !plan.isDeleted();
        boolean recipientAvailable = recipient != null && !recipient.deleted();
        return new AdminLicenseCodeSummary(
                licenseCode.getPublicId(),
                mask(licenseCode.getCode()),
                licenseCode.getStatus().name(),
                effectiveStatus.name(),
                licenseCode.getPlanId(),
                planAvailable ? plan.getName() : "Unavailable plan",
                planAvailable && plan.getType() != null ? plan.getType().name() : null,
                licenseCode.getIssuedAt(),
                licenseCode.getCodeExpiresAt(),
                licenseCode.getRedeemedByTenantId(),
                licenseCode.getRedeemedByTenantId() == null
                        ? null
                        : recipientAvailable ? recipient.name() : "Unavailable tenant",
                licenseCode.getSubscriptionId());
    }

    private static String mask(String code) {
        if (code == null) {
            return null;
        }
        if (code.length() <= 4) {
            return "••••";
        }
        return "•••• " + code.substring(code.length() - 4);
    }
}
