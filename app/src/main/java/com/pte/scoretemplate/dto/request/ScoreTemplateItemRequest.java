package com.pte.scoretemplate.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ScoreTemplateItemRequest(
        String taskType,
        String taskTypeKey,
        @NotBlank String section,
        int sequence,
        @PositiveOrZero int minCount,
        @PositiveOrZero int maxCount,
        @PositiveOrZero int prepSeconds,
        @PositiveOrZero int responseSeconds,
        @NotNull BigDecimal speakingWeight,
        @NotNull BigDecimal writingWeight,
        @NotNull BigDecimal readingWeight,
        @NotNull BigDecimal listeningWeight) {

    public ScoreTemplateItemRequest(String taskType, String section, int sequence, int minCount, int maxCount,
            int prepSeconds, int responseSeconds, BigDecimal speakingWeight, BigDecimal writingWeight,
            BigDecimal readingWeight, BigDecimal listeningWeight) {
        this(taskType, null, section, sequence, minCount, maxCount, prepSeconds, responseSeconds,
                speakingWeight, writingWeight, readingWeight, listeningWeight);
    }
}
