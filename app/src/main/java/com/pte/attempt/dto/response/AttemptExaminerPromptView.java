package com.pte.attempt.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Prompt projection read from an attempt's frozen exam item, including the
 * answer key the examiner marks against (never sent to the Student). The projection is intentionally owned by attempt because Examiner
 * work must display the content the student actually received, not the
 * current assessment snapshot.
 */
public record AttemptExaminerPromptView(
        UUID itemPublicId,
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        Integer minWordCount,
        Integer maxWordCount,
        List<Option> options,
        /** CEFR level of the bank question the item was frozen from; null when unknown. */
        String cefrLevel,
        /** Model answer, or the transcript of the prompt audio for listening-based items. */
        String referenceAnswerText,
        String correctAnswerText) {

    public AttemptExaminerPromptView(UUID itemPublicId, int orderIndex, String section, String taskType, String title,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, Integer minWordCount, Integer maxWordCount,
            List<Option> options, String cefrLevel) {
        this(itemPublicId, orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                minWordCount, maxWordCount, options, cefrLevel, null, null);
    }

    public AttemptExaminerPromptView(UUID itemPublicId, int orderIndex, String section, String taskType, String title,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, Integer minWordCount, Integer maxWordCount,
            List<Option> options) {
        this(itemPublicId, orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                minWordCount, maxWordCount, options, null, null, null);
    }

    /** One option with its answer key: whether it is correct and, for a gap item, the gap it belongs to. */
    public record Option(int orderIndex, String text, Integer blankIndex, boolean correct, Integer correctGapIndex) {

        public Option(int orderIndex, String text, Integer blankIndex) {
            this(orderIndex, text, blankIndex, false, null);
        }
    }
}
