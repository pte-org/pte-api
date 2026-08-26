package com.pte.admin.domain.event;

import java.util.UUID;

/** Payload for {@code QuotaGranted}. */
public record QuotaGrantedEvent(UUID transactionPublicId, UUID tenantPublicId, String packageName, int amount,
        UUID actorUserId) {
}
