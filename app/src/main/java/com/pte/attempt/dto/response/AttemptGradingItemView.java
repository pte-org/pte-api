package com.pte.attempt.dto.response;

import java.util.UUID;

/**
 * Exam item identity crossing the attempt/scoring boundary for coverage checks. {@code orderIndex} is the item's
 * 0-based position in the exam, null where the caller only knows the item's identity.
 */
public record AttemptGradingItemView(UUID itemPublicId, UUID examDefinitionPublicId, String taskType,
        Integer orderIndex) {

    public AttemptGradingItemView(UUID itemPublicId, UUID examDefinitionPublicId, String taskType) {
        this(itemPublicId, examDefinitionPublicId, taskType, null);
    }
}
