package com.pte.chat.internal.service;

import com.pte.chat.internal.domain.ChatMessage;

import java.util.List;
import java.util.UUID;

public record ChatLatestSession(UUID sessionId, List<ChatMessage> messages) {
}
