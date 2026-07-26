package com.pte.scheduling.domain.event;

import java.time.Instant;
import java.util.UUID;

public record SessionScheduledEvent(
        UUID sessionPublicId, UUID snapshotPublicId, UUID tenantId, Instant opensAt, Instant closesAt) {
}
