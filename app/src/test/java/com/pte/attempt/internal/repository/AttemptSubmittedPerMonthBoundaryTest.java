package com.pte.attempt.internal.repository;

import com.pte.attempt.AttemptOverviewService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Month-boundary bucketing against the real range query on the embedded database, using the
 * Asia/Ho_Chi_Minh calendar-month boundaries (local midnight on the 1st = 17:00Z on the previous
 * day). The boundary instants are the exact values asserted for the overview month bucketing in
 * {@code MonthBucketsTest}; attempts are placed on either side of a Vietnam month change that is
 * invisible in UTC.
 */
@DataJpaTest
@Import(AttemptOverviewService.class)
class AttemptSubmittedPerMonthBoundaryTest {

    /** 13 boundaries = 12 monthly buckets 2025-11 .. 2026-10 in Asia/Ho_Chi_Minh (now = 2026-10-13). */
    private static final List<Instant> TWELVE_MONTHS = List.of(
            Instant.parse("2025-10-31T17:00:00Z"), // 2025-11-01 00:00 +07
            Instant.parse("2025-11-30T17:00:00Z"), // 2025-12-01
            Instant.parse("2025-12-31T17:00:00Z"), // 2026-01-01
            Instant.parse("2026-01-31T17:00:00Z"), // 2026-02-01
            Instant.parse("2026-02-28T17:00:00Z"), // 2026-03-01
            Instant.parse("2026-03-31T17:00:00Z"), // 2026-04-01
            Instant.parse("2026-04-30T17:00:00Z"), // 2026-05-01
            Instant.parse("2026-05-31T17:00:00Z"), // 2026-06-01
            Instant.parse("2026-06-30T17:00:00Z"), // 2026-07-01
            Instant.parse("2026-07-31T17:00:00Z"), // 2026-08-01
            Instant.parse("2026-08-31T17:00:00Z"), // 2026-09-01
            Instant.parse("2026-09-30T17:00:00Z"), // 2026-10-01
            Instant.parse("2026-10-31T17:00:00Z")); // 2026-11-01

    @Autowired
    private AttemptOverviewService attemptService;

    @Autowired
    private ExamAttemptRepository attempts;

    private void submitted(UUID tenant, Instant submittedAt) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamVersionPublicId(UUID.randomUUID());
        attempt.setExamDefinitionPublicId(UUID.randomUUID());
        attempt.setTenantId(tenant);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setStartedAt(submittedAt.minusSeconds(900));
        attempt.setSubmittedAt(submittedAt);
        attempts.saveAndFlush(attempt);
    }

    @Test
    void attemptsOnEitherSideOfTheVietnamMidnightLandInDifferentMonths() {
        UUID tenant = UUID.randomUUID();
        // 2026-10-31T16:59:59Z = 23:59:59 on Oct 31 in Vietnam; 17:00:00Z = 00:00:00 on Nov 1.
        submitted(tenant, Instant.parse("2026-10-31T16:59:59Z"));
        submitted(tenant, Instant.parse("2026-10-31T17:00:00Z"));
        // Same UTC day as the October attempt but already November in Vietnam.
        submitted(tenant, Instant.parse("2026-10-31T23:30:00Z"));
        List<Instant> octAndNov = List.of(Instant.parse("2026-09-30T17:00:00Z"),
                Instant.parse("2026-10-31T17:00:00Z"), Instant.parse("2026-11-30T17:00:00Z"));

        List<Long> counts = attemptService.countSubmittedPerRange(tenant, octAndNov);

        assertThat(counts).containsExactly(1L, 2L);
    }

    @Test
    void twelveBucketsOldestFirstCountEachAttemptOnceAndNeverCrossTenants() {
        UUID tenant = UUID.randomUUID();
        submitted(tenant, Instant.parse("2025-11-30T17:00:00Z")); // 2025-12-01 00:00 in Vietnam: 2nd bucket
        submitted(tenant, Instant.parse("2026-10-05T02:00:00Z")); // current (October) bucket
        submitted(UUID.randomUUID(), Instant.parse("2026-10-06T02:00:00Z")); // other tenant

        List<Long> counts = attemptService.countSubmittedPerRange(tenant, TWELVE_MONTHS);

        assertThat(counts).hasSize(12);
        assertThat(counts.get(0)).isZero();
        assertThat(counts.get(1)).isEqualTo(1);
        assertThat(counts.get(11)).isEqualTo(1);
        assertThat(counts.stream().mapToLong(Long::longValue).sum()).isEqualTo(2);
    }

    @Test
    void attemptsOlderThanTwelveMonthsAreNotInTheSeries() {
        UUID tenant = UUID.randomUUID();
        submitted(tenant, Instant.parse("2025-10-31T16:59:59Z")); // 2025-10 in Vietnam: just before the window
        submitted(tenant, Instant.parse("2026-10-13T02:00:00Z"));

        List<Long> counts = attemptService.countSubmittedPerRange(tenant, TWELVE_MONTHS);

        assertThat(counts.stream().mapToLong(Long::longValue).sum()).isEqualTo(1);
        assertThat(counts.get(11)).isEqualTo(1);
    }
}
