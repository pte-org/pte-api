package com.pte.examdelivery.client.dto;

import java.util.List;
import java.util.UUID;

/** exam-delivery's own view of authoring's internal snapshot-content response — not a shared class. */
public record AuthoringSnapshotContentResponse(
        UUID publicId,
        String name,
        int version,
        UUID tenantId,
        List<Item> items) {

    public record Item(
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
            String optionsJson) {
    }
}
