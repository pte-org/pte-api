package com.pte.itembank.internal.generation.dto;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /question-generation/jobs} body. Field rules beyond shape are enforced by the job service. */
public record SubmitGenerationJobRequest(
        @NotBlank String taskType,
        @NotBlank String cefrLevel,
        @NotBlank String pool,
        @Size(max = QuestionGenerationConstants.MAX_TOPIC_HINT_LENGTH) String topicHint,
        @Min(1) @Max(QuestionGenerationConstants.MAX_ITEMS_PER_JOB_HARD_LIMIT) int count) {
}
