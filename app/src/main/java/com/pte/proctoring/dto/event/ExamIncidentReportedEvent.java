package com.pte.proctoring.dto.event;

import java.util.UUID;

/**
 * A proctor or host reported an incident during an exam session. Published inside the creating transaction, so a
 * listener that runs before commit is rolled back with it.
 */
public record ExamIncidentReportedEvent(UUID incidentPublicId, UUID tenantId, UUID sessionPublicId,
        UUID reporterPublicId, String title, String severity) {
}
