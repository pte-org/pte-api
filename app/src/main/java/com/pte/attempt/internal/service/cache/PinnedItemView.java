package com.pte.attempt.internal.service.cache;

import java.util.UUID;

/**
 * Cache-friendly, JSON-serializable projection of {@link com.pte.attempt.domain.PinnedItem}.
 * {@code preListenSeconds}/{@code preRecordSeconds} are non-null only for the 5
 * audio-prompt Speaking task types. {@code imageUrl} is non-null only when
 * {@code imagePromptRef} resolved successfully — unlike {@code audioUrl}, which
 * never reaches this projection at all (audio is served via a separate
 * on-demand endpoint reading {@code PinnedItem} directly), {@code imageUrl}
 * threads all the way to the student-facing response since a static image has
 * no replay-limit concern to gate behind an endpoint.
 */
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
        int responseSeconds,
        Integer preListenSeconds,
        Integer preRecordSeconds,
        String imageUrl) {
}
