package com.pte.chat.internal.service.quota;

import java.time.Instant;

public record ChatQuotaSnapshot(int used, int remaining, int limit, Instant resetAt) {
}
