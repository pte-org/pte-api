package com.pte.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Dashboard read model of one usable subscription. {@code planName} is null when the
 * plan can no longer be resolved; {@code planType} and {@code status} are enum names
 * (strings) so no domain enum leaks across the module boundary. Carries no licence
 * key or payment data.
 */
public record CurrentSubscriptionView(UUID publicId, String planName, String planType, Instant startsAt,
        Instant expiresAt, String status) {
}
