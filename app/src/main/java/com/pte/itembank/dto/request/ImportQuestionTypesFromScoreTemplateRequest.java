package com.pte.itembank.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Minimal data extracted from an existing score-template export. The UI owns
 * the import gesture; this contract only carries the source rows needed to
 * create missing question-type catalog entries.
 */
public record ImportQuestionTypesFromScoreTemplateRequest(
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 64) String taskType,
            @NotBlank @Size(max = 16) String section,
            @PositiveOrZero int sequence) {
    }
}
