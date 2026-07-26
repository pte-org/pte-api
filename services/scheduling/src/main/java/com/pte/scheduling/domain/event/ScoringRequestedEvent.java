package com.pte.scheduling.domain.event;

import java.util.UUID;

/**
 * Host command: "score every submitted attempt in this session." Originates
 * here (host-facing), consumed by scoring — scoring executes, it never decides
 * (ADR-002 host-gated scoring model).
 */
public record ScoringRequestedEvent(UUID sessionPublicId, UUID tenantId) {
}
