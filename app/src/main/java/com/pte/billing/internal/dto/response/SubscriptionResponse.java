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

    public static SubscriptionResponse from(SubscriptionView subscription) {
        return new SubscriptionResponse(
                subscription.publicId(),
                subscription.planId(),
                subscription.licenseKey(),
                subscription.startsAt(),
                subscription.expiresAt(),
                subscription.maxStudentsPerSession(),
                subscription.status().name(),
                subscription.activationSource().name());
    }
}
