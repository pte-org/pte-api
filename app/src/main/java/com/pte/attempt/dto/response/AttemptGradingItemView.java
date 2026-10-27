package com.pte.attempt.dto.response;

import java.util.UUID;

/** Exam item identity crossing the attempt/scoring boundary for coverage checks. */
public record AttemptGradingItemView(UUID itemPublicId, UUID examDefinitionPublicId, String taskType) {
}
