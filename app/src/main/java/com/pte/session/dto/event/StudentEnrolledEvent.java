package com.pte.session.dto.event;

import java.util.UUID;

/**
 * Published after a student is enrolled (single or bulk) commits successfully.
 * {@code notification} (Phase 09) listens for this via {@code
 * @TransactionalEventListener} to send an enrollment email — session has no
 * compile-time dependency on notification; this event type is the entire
 * contract between them.
 */
public record StudentEnrolledEvent(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
