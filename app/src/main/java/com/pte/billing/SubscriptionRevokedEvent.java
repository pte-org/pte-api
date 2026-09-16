package com.pte.billing;

import java.util.UUID;

/** Published when a redeemed subscription is revoked through its license code. */
public record SubscriptionRevokedEvent(UUID subscriptionPublicId) {
}
