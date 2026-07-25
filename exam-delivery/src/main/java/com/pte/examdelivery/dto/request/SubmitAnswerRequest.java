package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitAnswerRequest(
        @NotNull(message = "Task reference is required") UUID pinnedItemPublicId,
        @NotBlank(message = "Answer payload is required") String payload) {
}
