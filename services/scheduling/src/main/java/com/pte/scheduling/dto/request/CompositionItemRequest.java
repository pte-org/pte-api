package com.pte.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CompositionItemRequest(
        @NotBlank(message = "Task type is required") String taskType,
        @NotBlank(message = "Section is required") String section,
        int orderIndex,
        @Positive(message = "Timing override must be positive if provided") Integer timingOverrideSeconds) {
}
