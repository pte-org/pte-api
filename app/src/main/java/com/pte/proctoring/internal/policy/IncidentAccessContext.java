package com.pte.proctoring.internal.policy;

import java.util.UUID;

public record IncidentAccessContext(
        UUID tenantId,
        UUID actorPublicId,
        String actorRole,
        String actorName,
        boolean proctor) {
}
