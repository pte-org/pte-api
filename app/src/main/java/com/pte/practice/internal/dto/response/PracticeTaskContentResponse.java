package com.pte.practice.internal.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Student-safe content projection for the current practice item. Only prompt material appears here; reference
 * answers, correct answers and answer-key options are never part of this record.
 */
public record PracticeTaskContentResponse(
        String promptText,
        Integer minWordCount,
        Integer maxWordCount,
        UUID audioPromptRef,
        UUID imagePromptRef,
        List<Option> options,
        String audioUrl,
        String imageUrl) {

    public PracticeTaskContentResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(UUID publicId, String text, int orderIndex, Integer blankIndex) {
    }
}
