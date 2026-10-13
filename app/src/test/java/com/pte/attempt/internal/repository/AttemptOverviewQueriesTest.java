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

/** Attempt counts behind {@link AttemptOverviewService}: status, soft-delete, half-open ranges, tenant isolation. */
@DataJpaTest
@Import(AttemptOverviewService.class)
class AttemptOverviewQueriesTest {

    private static final Instant FROM = Instant.parse("2026-09-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-09-20T00:00:00Z");

    @Autowired
    private AttemptOverviewService service;

    @Autowired
    private ExamAttemptRepository attempts;

    private ExamAttempt attempt(UUID tenant, UUID student, AttemptStatus status, Instant startedAt,
                                Instant submittedAt, boolean deleted) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenant);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(student);
        attempt.setStatus(status);
        attempt.setStartedAt(startedAt);
        attempt.setSubmittedAt(submittedAt);
        attempt.setDeleted(deleted);
        return attempts.saveAndFlush(attempt);
    }

    private ExamAttempt submitted(UUID tenant, Instant submittedAt) {
        return attempt(tenant, UUID.randomUUID(), AttemptStatus.SUBMITTED, submittedAt.minusSeconds(1800),
                submittedAt, false);
    }

    // ---- countSubmitted ----

    @Test
    void submittedCountIsHalfOpenAndOnlyCountsSubmittedLiveAttemptsOfTheTenant() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        submitted(tenant, FROM); // lower bound included
        submitted(tenant, INSIDE);
        submitted(tenant, TO.minusSeconds(1));
        submitted(tenant, TO); // upper bound excluded
        submitted(tenant, FROM.minusSeconds(1)); // before window
        attempt(tenant, UUID.randomUUID(), AttemptStatus.SUBMITTED, INSIDE, INSIDE, true); // soft-deleted
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, INSIDE, false); // wrong status
        attempt(tenant, UUID.randomUUID(), AttemptStatus.CREATED, null, null, false);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.SUBMITTED, INSIDE, null, false); // no submit time
        submitted(other, INSIDE);

        assertThat(service.countSubmitted(tenant, FROM, TO)).isEqualTo(3);
        assertThat(service.countSubmitted(other, FROM, TO)).isEqualTo(1);
        assertThat(service.countSubmitted(UUID.randomUUID(), FROM, TO)).isZero();
    }

    @Test
    void adjacentSubmittedRangesPartitionTheAttempts() {
        UUID tenant = UUID.randomUUID();
        Instant middle = Instant.parse("2026-09-28T00:00:00Z");
        submitted(tenant, FROM);
        submitted(tenant, middle); // sits exactly on the shared boundary
        submitted(tenant, TO.minusSeconds(1));

        long first = service.countSubmitted(tenant, FROM, middle);
        long second = service.countSubmitted(tenant, middle, TO);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(first + second).isEqualTo(service.countSubmitted(tenant, FROM, TO));
    }

    // ---- countInProgress ----

    @Test
    void inProgressCountsOnlyLiveInProgressAttemptsOfTheTenantRegardlessOfAge() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, false);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, Instant.parse("2020-01-01T00:00:00Z"), null, false);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, true); // deleted
        attempt(tenant, UUID.randomUUID(), AttemptStatus.SUBMITTED, INSIDE, INSIDE, false);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.CREATED, null, null, false);
        attempt(other, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, false);
        attempt(other, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, false);

        assertThat(service.countInProgress(tenant)).isEqualTo(2);
        assertThat(service.countInProgress(other)).isEqualTo(2);
        assertThat(service.countInProgress(UUID.randomUUID())).isZero();
    }

    // ---- countDistinctActiveStudents ----

    @Test
    void distinctActiveStudentsCountEachStudentOnceWhoStartedInTheWindow() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID busy = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID boundary = UUID.randomUUID();
        attempt(tenant, busy, AttemptStatus.SUBMITTED, INSIDE, INSIDE.plusSeconds(60), false);
        attempt(tenant, busy, AttemptStatus.IN_PROGRESS, INSIDE.plusSeconds(3600), null, false);
        attempt(tenant, busy, AttemptStatus.SUBMITTED, INSIDE.plusSeconds(7200), INSIDE.plusSeconds(8000), false);
        attempt(tenant, second, AttemptStatus.IN_PROGRESS, INSIDE, null, false);
        attempt(tenant, boundary, AttemptStatus.IN_PROGRESS, FROM, null, false); // lower bound included
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, TO, null, false); // upper bound excluded
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, FROM.minusSeconds(1), null, false);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, true); // deleted
        attempt(tenant, UUID.randomUUID(), AttemptStatus.CREATED, null, null, false); // never started
        attempt(other, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, false);

        assertThat(service.countDistinctActiveStudents(tenant, FROM, TO)).isEqualTo(3);
        assertThat(service.countDistinctActiveStudents(other, FROM, TO)).isEqualTo(1);
    }

    @Test
    void sameStudentActiveInBothTenantsIsCountedPerTenantNotShared() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        attempt(tenantA, student, AttemptStatus.IN_PROGRESS, INSIDE, null, false);
        attempt(tenantB, student, AttemptStatus.IN_PROGRESS, INSIDE, null, false);
        attempt(tenantB, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, null, false);

        assertThat(service.countDistinctActiveStudents(tenantA, FROM, TO)).isEqualTo(1);
        assertThat(service.countDistinctActiveStudents(tenantB, FROM, TO)).isEqualTo(2);
    }

    // ---- countSubmittedPerRange ----

    @Test
    void perRangeReturnsOneCountPerConsecutiveRangeWithEveryBoundaryAttemptInExactlyOneBucket() {
        UUID tenant = UUID.randomUUID();
        Instant b0 = Instant.parse("2026-07-31T17:00:00Z");
        Instant b1 = Instant.parse("2026-08-31T17:00:00Z");
        Instant b2 = Instant.parse("2026-09-30T17:00:00Z");
        Instant b3 = Instant.parse("2026-10-31T17:00:00Z");
        submitted(tenant, b0); // first bucket (lower bound included)
        submitted(tenant, b1.minusSeconds(1)); // first bucket
        submitted(tenant, b1); // second bucket, never the first
        submitted(tenant, b2); // third bucket
        submitted(tenant, b3.minusSeconds(1)); // third bucket
        submitted(tenant, b3); // outside every range
        submitted(UUID.randomUUID(), b1.plusSeconds(10)); // other tenant

        List<Long> counts = service.countSubmittedPerRange(tenant, List.of(b0, b1, b2, b3));

        assertThat(counts).containsExactly(2L, 1L, 2L);
        assertThat(counts.stream().mapToLong(Long::longValue).sum()).isEqualTo(service.countSubmitted(tenant, b0, b3));
    }

    @Test
    void perRangeExcludesSoftDeletedAndNonSubmittedAttempts() {
        UUID tenant = UUID.randomUUID();
        attempt(tenant, UUID.randomUUID(), AttemptStatus.SUBMITTED, INSIDE, INSIDE, true);
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, INSIDE, false);
        submitted(tenant, INSIDE);

        assertThat(service.countSubmittedPerRange(tenant, List.of(FROM, TO))).containsExactly(1L);
    }

    @Test
    void perRangeWithFewerThanTwoBoundariesIsEmpty() {
        UUID tenant = UUID.randomUUID();
        submitted(tenant, INSIDE);

        assertThat(service.countSubmittedPerRange(tenant, null)).isEmpty();
        assertThat(service.countSubmittedPerRange(tenant, List.of())).isEmpty();
        assertThat(service.countSubmittedPerRange(tenant, List.of(FROM))).isEmpty();
    }

    @Test
    void perRangeOfAnEmptyTenantIsAllZeros() {
        assertThat(service.countSubmittedPerRange(UUID.randomUUID(), List.of(FROM, INSIDE, TO)))
                .containsExactly(0L, 0L);
    }
}
