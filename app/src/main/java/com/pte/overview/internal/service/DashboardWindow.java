package com.pte.overview.internal.service;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.exception.InvalidDashboardWindowException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Validated rolling window anchored at the injected clock. The current period is
 * {@code [start, end)} and the comparison period is {@code [previousStart, start)}
 * of equal length.
 */
public record DashboardWindow(int days, Instant previousStart, Instant start, Instant end) {

    /**
     * Parses the raw {@code windowDays} request value. Null or blank selects the
     * default; anything other than 7, 30 or 90 is rejected (the parameter is read
     * as text so a non-numeric value is a 400, not a type-mismatch 500).
     */
    public static DashboardWindow parse(String raw, Clock clock) {
        int days = OverviewConstants.DEFAULT_WINDOW_DAYS;
        if (raw != null && !raw.isBlank()) {
            try {
                days = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ex) {
                throw new InvalidDashboardWindowException();
            }
        }
        if (!OverviewConstants.ALLOWED_WINDOW_DAYS.contains(days)) {
            throw new InvalidDashboardWindowException();
        }
        return of(days, clock.instant());
    }

    public static DashboardWindow of(int days, Instant now) {
        Duration length = Duration.ofDays(days);
        return new DashboardWindow(days, now.minus(length.multipliedBy(2)), now.minus(length), now);
    }
}
