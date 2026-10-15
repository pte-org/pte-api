package com.pte.practice.internal.dto.response;

import java.util.List;

/** Student-safe content projection for the current practice item. */
public record PracticeTaskContentResponse(
        String promptText,
        Integer minWordCount,
        Integer maxWordCount,
        List<Option> options) {

    public PracticeTaskContentResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(String text, int orderIndex, Integer blankIndex) {
    }
}
