package com.pte.scoring.internal.rubric.dto;

import com.pte.scoring.internal.constant.RubricConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Numbers stay decimal until exact integer validation; Jackson must never truncate a submitted trait. */
public record RubricSimulationRequest(
        @NotBlank @Size(max = RubricConstants.MAX_TASK_TYPE_LENGTH) String taskType,
        UUID rubricVersionPublicId,
        @NotNull @Size(max = RubricConstants.MAX_TRAIT_SCORE_ENTRIES) Map<String, BigDecimal> traitScores,
        @Valid SimulationTextMetrics textMetrics,
        @Min(1) Integer wordCount) {

    public record SimulationTextMetrics(@Min(0) int wordCount, @Min(0) int sentenceCount,
                                        boolean allCaps, boolean punctuationPresent, boolean bulletOnly) {
    }
}
