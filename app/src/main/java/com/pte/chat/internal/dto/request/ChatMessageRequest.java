package com.pte.chat.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatMessageRequest(
        @NotNull(message = "sessionId is required") UUID sessionId,
        @NotBlank(message = "message is required") String message,
        String taskType,
        String language) {
}
