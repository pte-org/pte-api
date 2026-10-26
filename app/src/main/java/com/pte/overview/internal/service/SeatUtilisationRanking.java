package com.pte.overview.internal.service;

import com.pte.overview.internal.dto.response.AdminSectionData.SeatUtilisation;
import com.pte.tenancy.TenantSeatLimit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranks tenants by seat utilisation by joining the seat limits with the grouped student
 * counts in memory (never a per-tenant quota lookup). Tenants without a positive seat limit
 * are skipped: a ratio would be meaningless.
 */
public final class SeatUtilisationRanking {

    private static final int PERCENT = 100;
    private static final int PERCENT_SCALE = 1;

    /** Fullest first: students/limit descending (cross-multiplied, exact), then more students, then name. */
    private static final Comparator<SeatUtilisation> FULLEST_FIRST = (a, b) -> {
        int byRatio = Long.compare(b.students() * a.seatLimit(), a.students() * b.seatLimit());
        if (byRatio != 0) {
            return byRatio;
        }
        int byStudents = Long.compare(b.students(), a.students());
        return byStudents != 0 ? byStudents : a.name().compareToIgnoreCase(b.name());
    };

    private SeatUtilisationRanking() {
    }

    public static List<SeatUtilisation> top(List<TenantSeatLimit> limits, Map<UUID, Long> studentsByTenant, int n) {
        return limits.stream()
                .filter(limit -> limit.studentLimit() > 0)
                .map(limit -> toRow(limit, studentsByTenant.getOrDefault(limit.publicId(), 0L)))
                .sorted(FULLEST_FIRST)
                .limit(Math.max(0, n))
                .toList();
    }

    private static SeatUtilisation toRow(TenantSeatLimit limit, long students) {
        double pct = BigDecimal.valueOf(students)
                .multiply(BigDecimal.valueOf(PERCENT))
                .divide(BigDecimal.valueOf(limit.studentLimit()), PERCENT_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
        return new SeatUtilisation(limit.publicId(), limit.code(), limit.name(), students, limit.studentLimit(), pct);
    }
}
