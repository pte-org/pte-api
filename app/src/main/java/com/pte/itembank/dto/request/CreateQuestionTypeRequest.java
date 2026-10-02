package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Creates one standard PTE task type in the persisted catalog.
 *
 * <p>The task code is the stable integration key. Section, scoring and
 * authoring requirements are validated/derived from the server-side PTE task
 * catalog so a client cannot create a row that the question bank cannot use.
 */
public record CreateQuestionTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String shortName,
        @NotNull String section,
        @PositiveOrZero int displayOrder,
        @NotNull Boolean active) {
}
