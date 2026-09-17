package com.pte.assessment.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * FULL-fidelity snapshot content (prompts, options incl. correct flags,
 * reference/correct answers) — trusted application-call surface only
 * ({@link com.pte.assessment.AssessmentService#getFullContent}). {@code attempt}
 * pins this at attempt-create. Never expose this on a human-facing endpoint;
 * {@link SnapshotResponse} is the answer-stripped summary students/hosts can reach.
 */
public record SnapshotContentResponse(
        UUID publicId,
        String name,
        int version,
        UUID scoreTemplatePublicId,
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
