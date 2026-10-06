package com.pte.billing;

import java.util.UUID;

/** Published when a redeemed subscription is revoked through its license code. */
public record SubscriptionRevokedEvent(
        UUID subscriptionPublicId,
        UUID tenantPublicId,
        UUID planPublicId,
        String reason) {

    /** Source-compatible constructor for older publishers while they migrate. */
    public SubscriptionRevokedEvent(UUID subscriptionPublicId) {
        this(subscriptionPublicId, null, null, null);
    }
}
