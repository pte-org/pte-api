package com.pte.attempt.internal.service.cache;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.UUID;

/**
 * Cache-friendly, JSON-serializable projection of {@link com.pte.attempt.domain.PinnedItem}.
 * {@code preListenSeconds}/{@code preRecordSeconds} are non-null only for the 5
 * audio-prompt Speaking task types. {@code imageUrl} and {@code audioUrl} are the
 * resolved presigned URLs — non-null whenever the corresponding ref resolved
 * successfully. Images embed the URL directly (no replay-limit concern). Audio now
 * also embeds the URL so listening tasks can play immediately without an extra
 * on-demand round-trip; speaking tasks still use the /audio endpoint for replay-
 * limit enforcement.
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
        String imageUrl,
        String audioUrl,
        String taskTypeCode,
        TaskRuntimeProfileDescriptor runtime,
        String taskTypeDisplayName) {

    public PinnedItemView(UUID publicId, int orderIndex, String section, String taskType, String title,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount, String optionsJson,
            int prepSeconds, int responseSeconds, Integer preListenSeconds, Integer preRecordSeconds,
            String imageUrl) {
        this(publicId, orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson, prepSeconds,
                responseSeconds, preListenSeconds, preRecordSeconds, imageUrl, null, taskType, null, title);
    }

    public PinnedItemView(UUID publicId, int orderIndex, String section, String taskType, String title,
            String promptText, UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
            String correctAnswerText, Integer minWordCount, Integer maxWordCount, String optionsJson,
            int prepSeconds, int responseSeconds, Integer preListenSeconds, Integer preRecordSeconds,
            String imageUrl, String taskTypeCode, TaskRuntimeProfileDescriptor runtime) {
        this(publicId, orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson, prepSeconds,
                responseSeconds, preListenSeconds, preRecordSeconds, imageUrl, null, taskTypeCode, runtime, title);
    }
}
