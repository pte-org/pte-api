package com.pte.overview.internal.dto.response;

import java.time.Instant;

/**
 * Response metadata. The window is the rolling {@code [windowStart, windowEnd)}
 * interval; {@code zone} is the time zone used for calendar (month) buckets.
 */
public record DashboardMeta(Instant generatedAt, int windowDays, Instant windowStart, Instant windowEnd,
        String zone) {
}
