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
        List<SubscriptionImpact> expiringSubscriptions,
        long totalLossSeconds,
        long remainingSubscriptionCount,
        String impactDigest) {

    /** Backwards-compatible constructor for non-confirmation callers/tests. */
    public TenantSuspensionImpact(UUID tenantPublicId, int suspensionDays, Instant suspendedUntil,
            long activeSubscriptionCount, long totalDaysLost, List<SubscriptionImpact> expiringSubscriptions) {
        this(tenantPublicId, suspensionDays, suspendedUntil, activeSubscriptionCount, totalDaysLost,
                expiringSubscriptions, totalDaysLost * 86_400L,
                Math.max(0L, activeSubscriptionCount - expiringSubscriptions.size()), null);
    }

    public record SubscriptionImpact(
            UUID subscriptionPublicId,
            UUID planPublicId,
            Instant expiresAt,
            long daysLost,
            long lossSeconds,
            String planLabel) {

        /** Backwards-compatible constructor for existing API consumers. */
        public SubscriptionImpact(UUID subscriptionPublicId, UUID planPublicId, Instant expiresAt,
                long daysLost) {
            this(subscriptionPublicId, planPublicId, expiresAt, daysLost, daysLost * 86_400L, null);
        }
    }
}
