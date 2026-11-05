package com.pte.scoring.internal.vendor;

import com.pte.scoring.rubric.EffectiveRubric;

import java.util.UUID;

/**
 * Everything a scorer may need about one answer: the candidate's payload and
 * the item's authored content. Each scorer reads only the fields its task type
 * uses. A rubric-scored task type also carries the effective rubric of the
 * version resolved when the job runs, and that version's id; other task types
 * leave both null. {@code toString} names the task type only, so the
 * candidate's payload and the item's content never reach a log line.
 */
public record AiScoringRequest(String taskType, String payload, String promptText, String referenceAnswerText,
                               String correctAnswerText, UUID imagePromptRef, Integer minWordCount,
                               Integer maxWordCount, UUID tenantId, EffectiveRubric rubric,
                               UUID rubricVersionPublicId) {

    /** A request for a task type that is not scored through a rubric. */
    public AiScoringRequest(String taskType, String payload, String promptText, String referenceAnswerText,
                            String correctAnswerText, UUID imagePromptRef, Integer minWordCount,
                            Integer maxWordCount, UUID tenantId) {
        this(taskType, payload, promptText, referenceAnswerText, correctAnswerText, imagePromptRef, minWordCount,
                maxWordCount, tenantId, null, null);
    }

    @Override
    public String toString() {
        return "AiScoringRequest[taskType=" + taskType + "]";
    }
}
