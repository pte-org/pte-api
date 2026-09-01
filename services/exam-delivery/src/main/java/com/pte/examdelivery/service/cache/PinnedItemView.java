package com.pte.examdelivery.service.cache;

import java.util.UUID;

/**
 * Cache-friendly, JSON-serializable projection of {@link com.pte.examdelivery.domain.PinnedItem}.
 * {@code preListenSeconds}/{@code preRecordSeconds} are non-null only for the
 * 5 audio-prompt Speaking task types (plans/phat-speaking-dynamic-prep-timing).
 * {@code imageUrl} is non-null only when {@code imagePromptRef} resolved
 * successfully (mandatory for DESCRIBE_IMAGE, optional/absent elsewhere) —
 * unlike {@code audioUrl}, which never reaches this projection at all (audio
 * is served via a separate on-demand endpoint reading {@code PinnedItem}
 * directly), {@code imageUrl} threads all the way to the student-facing
 * response since a static image has no replay-limit concern to gate behind
 * an endpoint (plans/phat-describe-image-e2e). {@code imageUrlExpiresAt}
 * deliberately does NOT thread this far — nothing downstream needs it,
 * mirroring {@code audioUrlExpiresAt}'s own asymmetric threading.
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
