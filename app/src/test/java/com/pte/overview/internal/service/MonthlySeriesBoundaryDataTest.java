package com.pte.overview.internal.service;

import com.pte.attempt.AttemptOverviewService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Month-boundary bucketing end to end: the real boundaries from {@link MonthBuckets}
 * driving the real attempt range query on the embedded database, with attempts placed
 * on both sides of a Vietnam month change that is invisible in UTC.
 */
@DataJpaTest
@Import(AttemptOverviewService.class)
class MonthlySeriesBoundaryDataTest {

    private static final ZoneId VN = ZoneId.of(OverviewConstants.BUCKET_ZONE);

    @Autowired
    private AttemptOverviewService attemptService;

    @Autowired
    private ExamAttemptRepository attempts;

    private void submitted(UUID tenant, Instant submittedAt) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenant);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setStartedAt(submittedAt.minusSeconds(900));
        attempt.setSubmittedAt(submittedAt);
        attempts.saveAndFlush(attempt);
    }

    private List<MonthlyPoint> series(UUID tenant, Instant now) {
        List<Instant> boundaries = MonthBuckets.boundaries(now, VN, OverviewConstants.SERIES_MONTHS);
        return MonthBuckets.toPoints(now, VN, attemptService.countSubmittedPerRange(tenant, boundaries));
    }

    private static long countOf(List<MonthlyPoint> series, String month) {
        return series.stream().filter(p -> p.month().equals(month)).findFirst().orElseThrow().count();
    }

    @Test
    void attemptsOnEitherSideOfTheVietnamMidnightLandInDifferentMonths() {
        UUID tenant = UUID.randomUUID();
        // 2026-10-31T16:59:59Z = 23:59:59 on Oct 31 in Vietnam; 17:00:00Z = 00:00:00 on Nov 1.
        submitted(tenant, Instant.parse("2026-10-31T16:59:59Z"));
        submitted(tenant, Instant.parse("2026-10-31T17:00:00Z"));
        // Same UTC day as the October attempt but already November in Vietnam.
        submitted(tenant, Instant.parse("2026-10-31T23:30:00Z"));

        List<MonthlyPoint> series = series(tenant, Instant.parse("2026-11-15T03:00:00Z"));

        assertThat(countOf(series, "2026-10")).isEqualTo(1);
        assertThat(countOf(series, "2026-11")).isEqualTo(2);
        assertThat(series.stream().mapToLong(MonthlyPoint::count).sum()).isEqualTo(3);
    }

    @Test
    void seriesHasTwelveOldestFirstEntriesWithTheCurrentPartialMonthLastAndNoCrossTenantCounts() {
        UUID tenant = UUID.randomUUID();
        submitted(tenant, Instant.parse("2025-11-30T17:00:00Z")); // 2025-12-01 00:00 in Vietnam
        submitted(tenant, Instant.parse("2026-10-05T02:00:00Z"));
        submitted(UUID.randomUUID(), Instant.parse("2026-10-06T02:00:00Z")); // other tenant

        List<MonthlyPoint> series = series(tenant, Instant.parse("2026-10-13T03:00:00Z"));

        assertThat(series).hasSize(12);
        assertThat(series.get(0).month()).isEqualTo("2025-11");
        assertThat(series.get(11).month()).isEqualTo("2026-10");
        assertThat(series.get(11).partial()).isTrue();
        assertThat(series.subList(0, 11)).allMatch(point -> !point.partial());
        // 2025-11-30T17:00Z is 2025-12-01 00:00 in Vietnam: the second bucket, not the first.
        assertThat(countOf(series, "2025-11")).isZero();
        assertThat(countOf(series, "2025-12")).isEqualTo(1);
        assertThat(countOf(series, "2026-10")).isEqualTo(1);
    }

    @Test
    void attemptsOlderThanTwelveMonthsAreNotInTheSeries() {
        UUID tenant = UUID.randomUUID();
        submitted(tenant, Instant.parse("2025-10-31T16:59:59Z")); // 2025-10 in Vietnam: just before the window
        submitted(tenant, Instant.parse("2026-10-13T02:00:00Z"));

        List<MonthlyPoint> series = series(tenant, Instant.parse("2026-10-13T03:00:00Z"));

        assertThat(series.stream().mapToLong(MonthlyPoint::count).sum()).isEqualTo(1);
    }
}
