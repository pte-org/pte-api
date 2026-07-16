package com.aptis.modules.examdelivery.dto;

import jakarta.validation.constraints.NotBlank;

public record FlagViolationRequest(@NotBlank(message = "reason is required") String reason) {
}
