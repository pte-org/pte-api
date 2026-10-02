package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Canonical dynamic task-type create request. Runtime behavior is server-derived. */
public record CreateTaskTypeRequest(
        @NotBlank @Size(max = 64) String taskTypeKey,
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String shortName,
        @NotBlank String section,
        @NotBlank @Size(max = 96) String screenKey,
        @PositiveOrZero int contractVersion,
        @PositiveOrZero int displayOrder,
        @NotNull Boolean active) {
}
