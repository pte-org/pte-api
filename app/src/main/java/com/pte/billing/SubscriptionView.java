package com.pte.billing;

import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

/** Stable, non-ORM read contract exposed to other modules. */
public record SubscriptionView(
        UUID publicId,
        UUID tenantId,
        UUID planId,
        String licenseKey,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        SubscriptionStatus status,
        ActivationSource activationSource) {

    static SubscriptionView from(Subscription subscription) {
        return new SubscriptionView(
                subscription.getPublicId(),
                subscription.getTenantId(),
                subscription.getPlanId(),
                subscription.getLicenseKey(),
                subscription.getStartsAt(),
                subscription.getExpiresAt(),
                subscription.getMaxStudentsPerSession(),
                subscription.getStatus(),
                subscription.getActivationSource());
    }
}
