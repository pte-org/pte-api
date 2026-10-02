package com.pte.itembank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Canonical task-type update request. The key is deliberately immutable. */
public record UpdateTaskTypeRequest(
        @NotBlank @Size(max = 128) String displayName,
        @NotBlank @Size(max = 32) String shortName,
        @PositiveOrZero int displayOrder,
        @NotNull Boolean active,
        @Size(max = 96) String screenKey,
        @PositiveOrZero Integer contractVersion,
        String section) {
}
