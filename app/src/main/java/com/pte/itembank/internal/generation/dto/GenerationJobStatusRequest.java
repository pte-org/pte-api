package com.pte.itembank.internal.generation.dto;

import jakarta.validation.constraints.NotNull;

/** {@code PATCH /question-generation/jobs/{id}/status} body. */
public record GenerationJobStatusRequest(@NotNull GenerationJobAction action) {
}
