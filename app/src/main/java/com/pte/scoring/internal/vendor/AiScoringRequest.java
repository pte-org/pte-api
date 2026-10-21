package com.pte.scoring.internal.vendor;

import java.util.UUID;

/**
 * Everything a scorer may need about one answer: the candidate's payload and
 * the item's authored content. Each scorer reads only the fields its task type
 * uses. {@code toString} names the task type only, so the candidate's payload
 * and the item's content never reach a log line.
 */
public record AiScoringRequest(String taskType, String payload, String promptText, String referenceAnswerText,
                               String correctAnswerText, UUID imagePromptRef, Integer minWordCount,
                               Integer maxWordCount, UUID tenantId) {

    @Override
    public String toString() {
        return "AiScoringRequest[taskType=" + taskType + "]";
    }
}
