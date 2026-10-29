package com.pte.chat.internal.dto.request;

import com.pte.chat.internal.constant.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChatMessageRequest(
        @NotNull(message = ChatConstants.SESSION_ID_REQUIRED_MESSAGE) UUID sessionId,
        @NotBlank(message = ChatConstants.MESSAGE_REQUIRED_MESSAGE) String message,
        String taskType,
        String language) {
}
