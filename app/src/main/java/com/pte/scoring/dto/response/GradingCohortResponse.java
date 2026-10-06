package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

public record GradingCohortResponse(UUID cohortPublicId, UUID sessionPublicId, long cohortVersion,
        String status, String markingMode, int requiredAttemptCount, int excludedAttemptCount,
        int expectedItemCount, int satisfiedItemCount, boolean complete, List<String> blockingReasons) {
    public GradingCohortResponse {
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }
}
