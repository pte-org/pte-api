package com.pte.attempt.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Answer-key-free prompt projection read from an attempt's immutable pinned
 * item. The projection is intentionally owned by attempt because Examiner
 * work must display the content the student actually received, not the
 * current assessment snapshot.
 */
public record AttemptExaminerPromptView(
        UUID pinnedItemPublicId,
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        Integer minWordCount,
        Integer maxWordCount,
        List<Option> options) {

    /** No correctness marker or correct-gap index crosses into the Examiner view. */
    public record Option(int orderIndex, String text, Integer blankIndex) {
    }
}
