package com.pte.scoring.dto.request;

import java.util.UUID;

public record GradingCohortDispositionRequest(UUID attemptPublicId, String reason) {
}
