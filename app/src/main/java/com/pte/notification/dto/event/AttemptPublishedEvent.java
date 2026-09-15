package com.pte.notification.dto.event;

import java.util.UUID;

/**
 * Notifies the student that their report is now visible (reporting's
 * host-gated publish). Defined now so {@code notification}'s listener exists
 * ahead of time; {@code reporting} (Phase 10, not yet ported) will publish
 * this via {@code ApplicationEventPublisher} once its own publish command
 * exists — no publisher wired yet, same deferral precedent as every other
 * phase's "consumer exists before producer" gap.
 */
public record AttemptPublishedEvent(UUID attemptPublicId, UUID studentPublicId, UUID tenantId) {
}
