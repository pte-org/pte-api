package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.TrendValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds {@link TrendValue}s. The percentage is never invented: previous == 0 yields null. */
public final class TrendCalculator {

    private TrendCalculator() {
    }

    public static TrendValue of(long current, long previous) {
        if (previous == 0L) {
            return new TrendValue(current, previous, null);
        }
        double pct = BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return new TrendValue(current, previous, pct);
    }
}
