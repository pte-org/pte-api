package com.pte.chat.internal.service;

import java.util.UUID;

public record ChatSendCommand(UUID sessionId, String message, String taskType, String language) {
}
