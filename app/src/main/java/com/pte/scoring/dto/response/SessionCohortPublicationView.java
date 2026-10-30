package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Authoritative grading cohort membership projection consumed by reporting publication.
 */
public record SessionCohortPublicationView(
        UUID cohortPublicId,
        String markingMode,
        String status,
        List<UUID> requiredAttemptPublicIds,
        List<UUID> excludedAttemptPublicIds) {

    public SessionCohortPublicationView {
        requiredAttemptPublicIds = requiredAttemptPublicIds == null ? List.of() : List.copyOf(requiredAttemptPublicIds);
        excludedAttemptPublicIds = excludedAttemptPublicIds == null ? List.of() : List.copyOf(excludedAttemptPublicIds);
    }

    /** Whether the session is graded by assigned examiners, which adds session-level publication blockers. */
    public boolean manualExaminer() {
        return "MANUAL_EXAMINER".equals(markingMode);
    }
}
