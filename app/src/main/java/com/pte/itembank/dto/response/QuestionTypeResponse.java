package com.pte.itembank.dto.response;

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
        boolean usesOptionOrderAsCorrectPosition) {
}
