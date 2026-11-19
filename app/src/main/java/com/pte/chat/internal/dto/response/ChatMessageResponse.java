package com.pte.chat.internal.dto.response;

import java.time.Instant;

/** {@code remaining}, {@code limit}, {@code resetAt} are null when {@code unlimited}. */
public record ChatMessageResponse(String markdown, boolean onTopic, Integer remaining, Integer limit, Instant resetAt,
                                  boolean unlimited) {
}
