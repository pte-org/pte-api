package com.pte.session.dto.event;

import java.time.Instant;
import java.util.UUID;

/** Frozen, transaction-scoped signal emitted after the first exam snapshot is generated. */
public record ExamPreviewReadyEvent(
        UUID sessionPublicId,
        UUID tenantPublicId,
        UUID snapshotPublicId,
        String sessionCode,
        String sessionName,
        UUID actorUserPublicId,
        Instant occurredAt) {
}
