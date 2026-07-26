package com.pte.scoring.domain.event;

import java.util.UUID;

/** Payload for {@code AttemptScored} — fires once an attempt has zero PENDING answers left (honest completion, phase-07). */
public record AttemptScoredEvent(UUID attemptPublicId, UUID sessionPublicId, UUID tenantId) {
}
