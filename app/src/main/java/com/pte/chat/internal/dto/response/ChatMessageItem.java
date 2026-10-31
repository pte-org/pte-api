package com.pte.chat.internal.dto.response;

import java.time.Instant;

public record ChatMessageItem(String question, String answer, String status, Instant createdAt) {
}
