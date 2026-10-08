package com.pte.billing.internal.dto.response;

import com.pte.billing.SubscriptionView;

import java.time.Instant;
import java.util.UUID;

/** Masked platform-wide subscription projection; it never exposes the bearer key. */
public record PlatformSubscriptionResponse(
        UUID publicId,
        UUID tenantId,
        UUID planId,
        String maskedLicenseKey,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        String status,
        String activationSource) {

    public static PlatformSubscriptionResponse from(SubscriptionView view) {
        return new PlatformSubscriptionResponse(view.publicId(), view.tenantId(), view.planId(),
                mask(view.licenseKey()), view.startsAt(), view.expiresAt(), view.maxStudentsPerSession(),
                view.status().name(), view.activationSource().name());
    }

    private static String mask(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4 ? "••••" : "•••• " + value.substring(value.length() - 4);
    }
}
