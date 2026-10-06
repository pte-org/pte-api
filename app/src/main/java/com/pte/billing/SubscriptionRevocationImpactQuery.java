package com.pte.billing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Synchronous, resource-specific query event used by billing to ask the
 * session module for subscription impact without importing session internals.
 */
public final class SubscriptionRevocationImpactQuery {

    private final UUID subscriptionPublicId;
    private final UUID tenantPublicId;
    private SubscriptionRevocationImpact response;

    public SubscriptionRevocationImpactQuery(UUID subscriptionPublicId, UUID tenantPublicId) {
        this.subscriptionPublicId = subscriptionPublicId;
        this.tenantPublicId = tenantPublicId;
    }

    public UUID subscriptionPublicId() {
        return subscriptionPublicId;
    }

    public UUID tenantPublicId() {
        return tenantPublicId;
    }

    public synchronized void respond(SubscriptionRevocationImpact impact) {
        if (impact == null
                || !subscriptionPublicId.equals(impact.subscriptionPublicId())
                || !tenantPublicId.equals(impact.tenantPublicId())) {
            throw new IllegalStateException("Subscription revocation impact scope mismatch");
        }
        if (response != null) {
            throw new IllegalStateException("Subscription revocation impact has multiple responders");
        }
        response = impact;
    }

    public synchronized SubscriptionRevocationImpact requireResponse() {
        if (response == null) {
            throw new IllegalStateException("Subscription revocation impact has no responder");
        }
        return response;
    }

    public record SubscriptionRevocationImpact(
            UUID subscriptionPublicId,
            UUID tenantPublicId,
            List<UUID> scheduledSessionPublicIds,
            int scheduledCount,
            int openCount,
            int closedCount) {

        public SubscriptionRevocationImpact {
            if (subscriptionPublicId == null || tenantPublicId == null) {
                throw new IllegalArgumentException("Subscription revocation impact scope is required");
            }
            List<UUID> sorted = new ArrayList<>(scheduledSessionPublicIds == null
                    ? List.of() : scheduledSessionPublicIds);
            sorted.sort(Comparator.naturalOrder());
            if (sorted.stream().anyMatch(java.util.Objects::isNull)
                    || sorted.stream().distinct().count() != sorted.size()) {
                throw new IllegalArgumentException("Scheduled session scope must be unique");
            }
            scheduledSessionPublicIds = List.copyOf(sorted);
            if (scheduledCount != sorted.size() || openCount < 0 || closedCount < 0) {
                throw new IllegalArgumentException("Invalid subscription revocation impact counts");
            }
        }

        public static SubscriptionRevocationImpact empty(UUID subscriptionPublicId, UUID tenantPublicId) {
            return new SubscriptionRevocationImpact(subscriptionPublicId, tenantPublicId, List.of(), 0, 0, 0);
        }
    }
}
