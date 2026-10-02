package com.pte.itembank.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.UUID;

public record QuestionTypeResponse(
        UUID publicId,
        String code,
        String displayName,
        String shortName,
        String section,
        boolean scored,
        boolean active,
        int displayOrder,
        boolean requiresAudioPrompt,
        boolean requiresImagePrompt,
        boolean requiresPromptText,
        boolean requiresOptions,
        boolean requiresCorrectAnswer,
        boolean requiresWordCount,
        boolean requiresSingleCorrectOption,
        boolean usesOptionOrderAsCorrectPosition,
        TaskRuntimeProfileDescriptor runtime,
        String taskTypeCode,
        String taskTypeKey,
        String screenKey,
        Integer contractVersion,
        TaskTypeReadinessResponse readiness,
        TaskTypeEditabilityResponse editability) {

    /** Compatibility constructor for callers compiled against the old DTO shape. */
    public QuestionTypeResponse(UUID publicId, String code, String displayName, String shortName,
            String section, boolean scored, boolean active, int displayOrder,
            boolean requiresAudioPrompt, boolean requiresImagePrompt, boolean requiresPromptText,
            boolean requiresOptions, boolean requiresCorrectAnswer, boolean requiresWordCount,
            boolean requiresSingleCorrectOption, boolean usesOptionOrderAsCorrectPosition) {
        this(publicId, code, displayName, shortName, section, scored, active, displayOrder,
                requiresAudioPrompt, requiresImagePrompt, requiresPromptText, requiresOptions,
                requiresCorrectAnswer, requiresWordCount, requiresSingleCorrectOption,
                usesOptionOrderAsCorrectPosition, null, code, code, null, null, null, null);
    }
}
