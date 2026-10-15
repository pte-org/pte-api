package com.pte.proctoring.dto.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentHistoryItemView(
        String id,
        UUID incidentPublicId,
        String actorName,
        String actorRole,
        String action,
        String fromStatus,
        String toStatus,
        String notes,
        Instant createdAt
) {
}
