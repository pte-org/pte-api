package com.pte.chat.internal.service;

import java.util.List;

/**
 * What a student sent. {@code history} is the earlier part of the open conversation, kept by the browser: the
 * server stores nothing between two messages. It is untrusted until {@link ChatSendValidator} has checked it.
 */
public record ChatSendCommand(String message, String taskType, String language, String exercisePrompt,
                              List<ChatHistoryEntry> history) {

    public ChatSendCommand(String message, String taskType, String language) {
        this(message, taskType, language, null, null);
    }

    public ChatSendCommand(String message, String taskType, String language, String exercisePrompt) {
        this(message, taskType, language, exercisePrompt, null);
    }
}
