package com.pte.chat.internal.dto.request;

import com.pte.chat.internal.constant.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code exercisePrompt} is the text of the exercise on screen, optional context for the answer. */
public record ChatMessageRequest(
        @NotNull(message = ChatConstants.SESSION_ID_REQUIRED_MESSAGE) UUID sessionId,
        @NotBlank(message = ChatConstants.MESSAGE_REQUIRED_MESSAGE) String message,
        String taskType,
        String language,
        String exercisePrompt) {

    public ChatMessageRequest(UUID sessionId, String message, String taskType, String language) {
        this(sessionId, message, taskType, language, null);
    }
}
