package com.pte.overview.internal.dto.response;

/**
 * Period-over-period figure on rolling windows. {@code changePct} is computed
 * by the backend and is null when {@code previous} is zero (no honest ratio).
 */
public record TrendValue(long current, long previous, Double changePct) {
}
