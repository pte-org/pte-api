package com.pte.scoretemplate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ScoreTemplateItemRequest(
        @NotBlank String taskType,
        @NotBlank String section,
        int sequence,
        @PositiveOrZero int minCount,
        @PositiveOrZero int maxCount,
        @PositiveOrZero int prepSeconds,
        @PositiveOrZero int responseSeconds,
        @NotBlank String scoringMethod,
        @NotNull BigDecimal overallWeight,
        @NotNull BigDecimal speakingWeight,
        @NotNull BigDecimal writingWeight,
        @NotNull BigDecimal readingWeight,
        @NotNull BigDecimal listeningWeight) {
}
