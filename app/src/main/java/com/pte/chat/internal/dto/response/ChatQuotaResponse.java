package com.pte.chat.internal.dto.response;

import java.time.Instant;

public record ChatQuotaResponse(int used, int remaining, int limit, Instant resetAt) {
}
