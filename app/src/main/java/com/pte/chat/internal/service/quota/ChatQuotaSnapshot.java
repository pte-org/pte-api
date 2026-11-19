package com.pte.chat.internal.service.quota;

import java.time.Instant;

/** Today's use. When {@code unlimited}, {@code remaining}, {@code limit} and {@code resetAt} are null. */
public record ChatQuotaSnapshot(int used, Integer remaining, Integer limit, Instant resetAt, boolean unlimited) {
}
