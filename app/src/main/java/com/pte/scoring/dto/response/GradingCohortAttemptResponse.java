package com.pte.scoring.dto.response;

import java.util.UUID;

public record GradingCohortAttemptResponse(UUID attemptPublicId, UUID studentPublicId, String status,
        boolean excluded, String dispositionReason) {
}
