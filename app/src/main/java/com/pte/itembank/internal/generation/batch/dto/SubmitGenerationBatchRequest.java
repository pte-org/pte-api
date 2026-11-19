package com.pte.itembank.internal.generation.batch.dto;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code POST /question-generation/batches} body. Rules beyond shape (limits, catalog, quota) live in the service. */
public record SubmitGenerationBatchRequest(
        @NotBlank String pool,
        @NotNull @Size(min = 1, max = BatchGenerationConstants.MAX_ROWS_PER_BATCH,
                message = BatchGenerationConstants.MAX_ROWS_MESSAGE)
        List<@Valid @NotNull Row> rows) {

    /** One line of the batch form: how many questions of one task type and level, spread over the topics. */
    public record Row(
            @NotBlank String taskType,
            @NotBlank String cefrLevel,
            @NotNull Integer count,
            @Size(max = BatchGenerationConstants.MAX_TOPIC_HINTS_PER_ROW,
                    message = BatchGenerationConstants.MAX_HINTS_MESSAGE)
            List<@NotBlank String> topicHints,
            /** AUTO (default), CHART or PICTURE; only Describe Image uses it. */
            String imageKind) {
    }
}
