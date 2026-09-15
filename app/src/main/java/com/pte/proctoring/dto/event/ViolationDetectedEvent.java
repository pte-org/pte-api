package com.pte.proctoring.dto.event;

import com.pte.proctoring.domain.enums.ViolationType;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a violation is recorded and the hash chain advances,
 * inside the same transaction that will commit it — {@code notification}
 * (Phase 09) listens via {@code @TransactionalEventListener} (fires only
 * after commit succeeds). Carries no specific recipient (a proctor session
 * has no single "owner" to notify) — notification fans this out to every
 * HOST_ADMIN in the tenant instead.
 */
public record ViolationDetectedEvent(UUID attemptPublicId, UUID sessionPublicId, ViolationType violationType,
                                      String detail, Instant detectedAt, UUID tenantId) {
}
