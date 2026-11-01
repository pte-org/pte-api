package com.pte.chat.internal.service;

import com.pte.chat.internal.client.ChatTurn;

import java.util.List;

/**
 * A send request after validation: trimmed message, whitelisted task type (or null), a known language, the cleaned
 * exercise text (or null) and the earlier turns, already alternating and capped.
 */
record ChatSendInput(String message, String taskType, String language, String exercisePrompt, List<ChatTurn> history) {

    ChatSendInput(String message, String taskType, String language) {
        this(message, taskType, language, null, List.of());
    }
}
