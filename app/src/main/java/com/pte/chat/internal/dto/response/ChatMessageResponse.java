package com.pte.chat.internal.dto.response;

import java.time.Instant;

public record ChatMessageResponse(String markdown, boolean onTopic, int remaining, int limit, Instant resetAt) {
}
