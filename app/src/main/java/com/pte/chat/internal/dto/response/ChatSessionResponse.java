package com.pte.chat.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record ChatSessionResponse(UUID sessionId, List<ChatMessageItem> messages) {
}
