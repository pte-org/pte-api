package com.pte.proctoring.dto.incident;

import java.time.Instant;
import java.util.UUID;

public record IncidentView(
        UUID incidentPublicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        String studentName,
        UUID attemptPublicId,
        String title,
        String description,
        String category,
        String severity,
        String status,
        String reporterRole,
        String reporterName,
        String currentOwnerRole,
        String currentOwnerName,
        String resolutionSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
