package com.pte.assessment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** The host's unsaved template rows; only identity, section and the four skill weights matter here. */
public record NormalizeSkillWeightsRequest(@NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotBlank String taskTypeKey,
            @NotBlank String section,
            @PositiveOrZero BigDecimal speakingWeight,
            @PositiveOrZero BigDecimal writingWeight,
            @PositiveOrZero BigDecimal readingWeight,
            @PositiveOrZero BigDecimal listeningWeight) {
    }
}
