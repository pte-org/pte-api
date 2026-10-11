package com.pte.proctoring.internal.dto.response;

import java.time.Instant;

/**
 * Whole-session enrolled student counts categorized by their latest attempt status.
 */
public record MonitoringSummaryResponse(
        long registeredCount,
        long notStartedCount,
        long inProgressCount,
        long submittedCount,
        long createdCount,
        Instant snapshotAt
) {
}
