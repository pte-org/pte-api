package com.pte.examdelivery.service.cache;

import java.util.UUID;

/** Cache-friendly, JSON-serializable projection of {@link com.pte.examdelivery.domain.PinnedItem}. */
public record PinnedItemView(
        UUID publicId,
        int orderIndex,
        String section,
        String taskType,
        String title,
        String promptText,
        UUID audioPromptRef,
        UUID imagePromptRef,
        String referenceAnswerText,
        String correctAnswerText,
        Integer minWordCount,
        Integer maxWordCount,
        String optionsJson,
        int prepSeconds,
        int responseSeconds) {
}
