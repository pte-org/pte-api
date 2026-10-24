package com.pte.scoring;

import java.util.UUID;

/**
 * One practice answer plus the authored content the exam passes to its scorers.
 * {@code payload} uses the exam shape: a media id for audio, raw text for
 * writing, a comma-separated selection for objective types. {@code optionsJson}
 * is the frozen option snapshot objective scoring reads. {@code toString} names
 * the task type only so the answer never reaches a log line.
 */
public record PracticeScoringRequest(String taskType, String payload, String promptText,
        String referenceAnswerText, String correctAnswerText, UUID imagePromptRef, Integer minWordCount,
        Integer maxWordCount, String optionsJson, UUID tenantId) {

    @Override
    public String toString() {
        return "PracticeScoringRequest[taskType=" + taskType + "]";
    }
}
