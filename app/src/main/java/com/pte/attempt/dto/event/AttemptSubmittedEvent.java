package com.pte.attempt.dto.event;

import java.util.UUID;

/**
 * Published when an attempt reaches {@code SUBMITTED}, by the student's own submit or a proctor's
 * force-submit. Its answers are final from here on, so {@code scoring} listens
 * ({@code @TransactionalEventListener}, after commit) to copy them into its work-state table. That
 * only makes them visible for review; it never starts scoring, which stays the host's command.
 */
public record AttemptSubmittedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID tenantId) {
}
