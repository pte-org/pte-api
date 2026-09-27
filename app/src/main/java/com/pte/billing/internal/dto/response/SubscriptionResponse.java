package com.pte.billing.internal.dto.response;

import com.pte.billing.SubscriptionView;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID publicId,
        UUID planId,
        String licenseKey,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        String status,
        String activationSource) {

    /**
     * Masked to the last 4 characters — the full key is only ever sent by the
     * dedicated {@code POST /subscriptions/{publicId}/license-key/reveal} endpoint,
     * gated on the caller re-entering their password. Listing subscriptions is a
     * much more common, much less guarded action than revealing a live credential,
     * so the full key must never ride along with it.
     */
    public static SubscriptionResponse from(SubscriptionView subscription) {
        return new SubscriptionResponse(
                subscription.publicId(),
                subscription.planId(),
                mask(subscription.licenseKey()),
                subscription.startsAt(),
                subscription.expiresAt(),
                subscription.maxStudentsPerSession(),
                subscription.status().name(),
                subscription.activationSource().name());
    }

    private static String mask(String licenseKey) {
        if (licenseKey == null) {
            return null;
        }
        if (licenseKey.length() <= 4) {
            return "••••";
        }
        return "•••• " + licenseKey.substring(licenseKey.length() - 4);
    }
}
