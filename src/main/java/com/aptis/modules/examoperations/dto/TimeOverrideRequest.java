package com.aptis.modules.examoperations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TimeOverrideRequest(
        @NotNull(message = "skill is required") String skill,
        @NotNull(message = "part is required") Integer part,
        @NotNull(message = "overrideMinutes is required")
        @Min(value = 1, message = "overrideMinutes must be at least 1")
        Integer overrideMinutes) {
}
