package com.pte.chat.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(String markdown, boolean onTopic, boolean fromCache, int remaining, int limit,
                                  Instant resetAt, UUID sessionId) {
}
