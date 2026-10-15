package com.pte.itembank.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Student-safe question content selected from the PRACTICE pool.
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
        List<Option> options) {

    public PracticeQuestionContent {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(String text, int orderIndex, Integer blankIndex) {
    }
}
