package com.pte.reporting.domain.event;

import java.util.UUID;

/** Payload for {@code AttemptPublished}. Contract exists for future consumers (e.g. notification, Phase 11) — nothing consumes it yet in Milestone 1. */
public record AttemptPublishedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
}
