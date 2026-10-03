package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

public record GradingCohortPreviewResponse(UUID sessionPublicId, String previewVersion, boolean finalized,
        UUID cohortPublicId, String markingMode, int submittedAttemptCount, int outstandingAttemptCount,
        List<GradingCohortAttemptResponse> attempts, List<String> blockingReasons) {
    public GradingCohortPreviewResponse {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
    }
}
