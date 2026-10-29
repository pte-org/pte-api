package com.pte.chat.internal.service;

import java.util.UUID;

/**
 * A send request after validation: trimmed message, whitelisted task type (or null), a known language and the
 * cleaned exercise text (or null).
 */
record ChatSendInput(UUID sessionId, String message, String taskType, String language, String exercisePrompt) {

    ChatSendInput(UUID sessionId, String message, String taskType, String language) {
        this(sessionId, message, taskType, language, null);
    }
}
