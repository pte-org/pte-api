package com.aptis.modules.examdelivery.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ExtendTimeRequest(
        @NotNull(message = "minutes is required")
        @Min(value = 1, message = "minutes must be at least 1")
        Integer minutes,
        String skill,
        Integer part) {
}
