package com.pte.itembank.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Student-safe question content selected from a published practice channel
 * revision.
 *
 * <p>Correct-answer fields are intentionally absent. The practice module
 * snapshots this projection into its own session item before delivery.</p>
 */
public record PracticeQuestionContent(
        UUID publicId,
        String taskTypeKey,
        String promptText,
        Integer minWordCount,
        Integer maxWordCount,
        UUID audioPromptRef,
        UUID imagePromptRef,
        List<Option> options,
        /** Student-safe guidance on how to approach the task; it never contains the answer. */
        String answerHintVi,
        String answerHintEn) {

    public PracticeQuestionContent {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** Content without answer hints. */
    public PracticeQuestionContent(UUID publicId, String taskTypeKey, String promptText, Integer minWordCount,
            Integer maxWordCount, UUID audioPromptRef, UUID imagePromptRef, List<Option> options) {
        this(publicId, taskTypeKey, promptText, minWordCount, maxWordCount, audioPromptRef, imagePromptRef,
                options, null, null);
    }

    public record Option(UUID publicId, String text, int orderIndex, Integer blankIndex) {
    }
}
