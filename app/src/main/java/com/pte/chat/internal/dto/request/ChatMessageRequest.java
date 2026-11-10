package com.pte.chat.internal.dto.request;

import com.pte.chat.internal.constant.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code exercisePrompt} is the text of the exercise on screen, optional context for the answer. {@code history}
 * is the earlier part of the open conversation: the browser keeps it, the server stores nothing.
 */
public record ChatMessageRequest(
        @NotBlank(message = ChatConstants.MESSAGE_REQUIRED_MESSAGE) String message,
        String taskType,
        String language,
        String exercisePrompt,
        @Size(max = ChatConstants.HISTORY_MAX_ITEMS, message = ChatConstants.HISTORY_TOO_LONG_MESSAGE)
        List<ChatHistoryItem> history) {

    public ChatMessageRequest(String message, String taskType, String language) {
        this(message, taskType, language, null, null);
    }
}
