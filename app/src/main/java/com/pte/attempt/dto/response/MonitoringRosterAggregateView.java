package com.pte.attempt.dto.response;

/**
 * Whole-session enrolled-latest status aggregate, independent of paging.
 */
public record MonitoringRosterAggregateView(
        long registeredCount,
        long notStartedCount,
        long inProgressCount,
        long submittedCount,
        long createdCount
) {
}
