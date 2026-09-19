package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Editable catalog metadata for one existing PTE question type. */
public record UpdateQuestionTypeRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String shortName,
        @PositiveOrZero int displayOrder,
        @NotNull Boolean active,
        @NotNull Boolean requiresAudioPrompt,
        @NotNull Boolean requiresImagePrompt,
        @NotNull Boolean requiresPromptText,
        @NotNull Boolean requiresOptions,
        @NotNull Boolean requiresCorrectAnswer,
        @NotNull Boolean requiresWordCount,
        @NotNull Boolean requiresSingleCorrectOption,
        @NotNull Boolean usesOptionOrderAsCorrectPosition) {
}
