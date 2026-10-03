package com.pte.scoring;

import java.util.UUID;

/** Published only on the FROZEN-to-COMPLETED grading transition. */
public record SessionGradingCompletedEvent(UUID tenantPublicId, UUID sessionPublicId,
        UUID cohortPublicId, long cohortVersion, int requiredAttemptCount) {
}
