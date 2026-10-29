package com.pte.chat.internal.service;

import java.util.UUID;

/** A send request after validation: trimmed message, whitelisted task type (or null) and a known language. */
record ChatSendInput(UUID sessionId, String message, String taskType, String language) {
}
