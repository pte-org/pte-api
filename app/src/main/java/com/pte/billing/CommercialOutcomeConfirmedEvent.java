package com.pte.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the authoritative billing transaction after one commercial
 * outcome is committed in memory. Notification consumes this public event;
 * billing never imports notification internals.
 */
public record CommercialOutcomeConfirmedEvent(
        UUID tenantPublicId,
        UUID planPublicId,
        CommercialOutcomeType outcomeType,
        CommercialActivationSource activationSource,
        CommercialActivationTarget targetType,
        UUID targetPublicId,
        Integer grantedStudentSlots,
        /** Start of the activated subscription; null for student capacity. */
        Instant startsAt) {
}
