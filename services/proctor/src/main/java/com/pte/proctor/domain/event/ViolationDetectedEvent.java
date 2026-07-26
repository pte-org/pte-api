package com.pte.proctor.domain.event;

import com.pte.proctor.domain.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

/** Outbox payload for {@code outbox.event.ViolationEvent}. Not consumed this phase (Phase 11 notification, reporting). */
public record ViolationDetectedEvent(UUID attemptPublicId, UUID sessionPublicId, ViolationType violationType,
                                      String detail, Instant detectedAt, UUID tenantId) {
}
