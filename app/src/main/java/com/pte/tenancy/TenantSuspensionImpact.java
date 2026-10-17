package com.pte.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only commercial impact of applying a tenant suspension window. */
public record TenantSuspensionImpact(
        UUID tenantPublicId,
        int suspensionDays,
        Instant suspendedUntil,
        long activeSubscriptionCount,
        long totalDaysLost,
        List<SubscriptionImpact> expiringSubscriptions) {

    public record SubscriptionImpact(
            UUID subscriptionPublicId,
            UUID planPublicId,
            Instant expiresAt,
            long daysLost) {
    }
}
