package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.MonthlyAmount;
import com.pte.overview.internal.dto.response.MonthlyPoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendar-month buckets in a fixed zone. The last bucket is the month containing
 * {@code now} and is flagged partial; earlier buckets are complete months.
 */
public final class MonthBuckets {

    private MonthBuckets() {
    }

    /** Ascending instants delimiting {@code months} buckets: {@code months + 1} boundaries, oldest start first. */
    public static List<Instant> boundaries(Instant now, ZoneId zone, int months) {
        YearMonth current = YearMonth.from(now.atZone(zone));
        List<Instant> boundaries = new ArrayList<>(months + 1);
        for (int offset = months - 1; offset >= 0; offset--) {
            boundaries.add(current.minusMonths(offset).atDay(1).atStartOfDay(zone).toInstant());
        }
        boundaries.add(current.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
        return boundaries;
    }

    /** Pairs each count with its {@code yyyy-MM} label; {@code counts.size()} must equal {@code months}. */
    public static List<MonthlyPoint> toPoints(Instant now, ZoneId zone, List<Long> counts) {
        int months = counts.size();
        YearMonth current = YearMonth.from(now.atZone(zone));
        List<MonthlyPoint> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = current.minusMonths(months - 1L - i);
            points.add(new MonthlyPoint(month.toString(), counts.get(i), i == months - 1));
        }
        return points;
    }

    /** Like {@link #toPoints} for exact amounts; {@code amounts.size()} must equal the number of months. */
    public static List<MonthlyAmount> toAmountPoints(Instant now, ZoneId zone, List<BigDecimal> amounts) {
        int months = amounts.size();
        YearMonth current = YearMonth.from(now.atZone(zone));
        List<MonthlyAmount> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = current.minusMonths(months - 1L - i);
            points.add(new MonthlyAmount(month.toString(), amounts.get(i), i == months - 1));
        }
        return points;
    }
}
