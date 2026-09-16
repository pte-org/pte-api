package com.pte.reporting.dto.event;

import java.util.UUID;

/**
 * Published after a host's publish command marks an {@code AttemptReport}
 * visible, inside the same transaction that commits it — {@code
 * notification} listens via {@code @TransactionalEventListener} (fires only
 * after commit succeeds), to email the student their report is ready.
 */
public record AttemptPublishedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
