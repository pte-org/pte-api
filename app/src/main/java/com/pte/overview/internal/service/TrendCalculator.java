package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.TrendValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds {@link TrendValue}s. The percentage is never invented: previous == 0 yields null. */
public final class TrendCalculator {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 1;

    private TrendCalculator() {
    }

    public static TrendValue of(long current, long previous) {
        if (previous == 0L) {
            return new TrendValue(current, previous, null);
        }
        return new TrendValue(current, previous,
                percentChange(BigDecimal.valueOf(current), BigDecimal.valueOf(previous)));
    }

    /**
     * Percentage change from {@code previous} to {@code current} to one decimal, computed on the exact
     * amounts; null when {@code previous} is zero (no honest ratio).
     */
    public static Double percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(PERCENT)
                .divide(previous, PERCENT_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
