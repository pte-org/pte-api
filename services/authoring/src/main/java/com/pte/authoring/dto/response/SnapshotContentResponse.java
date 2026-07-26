package com.pte.authoring.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * FULL-fidelity snapshot content (prompts, options incl. correct flags,
 * reference/correct answers) for the {@code /internal/**} surface only —
 * exam-delivery pins this at attempt-create. Never exposed on the public
 * {@code /snapshots/{id}} endpoint ({@link SnapshotResponse} is the
 * answer-stripped summary students/hosts can reach).
 */
public record SnapshotContentResponse(
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
