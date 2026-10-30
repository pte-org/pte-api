package com.pte.attempt.internal.repository;

import com.pte.attempt.AttemptOverviewService;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform-wide distinct active student count behind
 * {@link AttemptOverviewService#countDistinctActiveStudentsPlatformWide(Instant, Instant)}: half-open started_at range, soft
 * delete, one count per student across tenants and the previous/current window pair.
 */
@DataJpaTest
@Import(AttemptOverviewService.class)
class AttemptPlatformOverviewQueriesTest {

    private static final Instant FROM = Instant.parse("2026-09-13T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-13T03:00:00Z");
    private static final Instant PREVIOUS = Instant.parse("2026-08-14T03:00:00Z");
    private static final Instant INSIDE = Instant.parse("2026-09-20T00:00:00Z");

    @Autowired
    private AttemptOverviewService service;

    @Autowired
    private ExamAttemptRepository attempts;

    private void attempt(UUID tenant, UUID student, AttemptStatus status, Instant startedAt, boolean deleted) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamVersionPublicId(UUID.randomUUID());
        attempt.setExamDefinitionPublicId(UUID.randomUUID());
        attempt.setTenantId(tenant);
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(student);
        attempt.setStatus(status);
        attempt.setStartedAt(startedAt);
        attempt.setDeleted(deleted);
        attempts.saveAndFlush(attempt);
    }

    private void started(UUID tenant, UUID student, Instant at) {
        attempt(tenant, student, AttemptStatus.IN_PROGRESS, at, false);
    }

    @Test
    void countsEveryStudentWhoStartedInTheWindowAcrossAllTenantsExactlyOnce() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID busy = UUID.randomUUID();
        started(tenantA, busy, INSIDE);
        started(tenantA, busy, INSIDE.plusSeconds(3600));
        started(tenantA, busy, INSIDE.plusSeconds(7200));
        started(tenantA, UUID.randomUUID(), INSIDE);
        started(tenantB, UUID.randomUUID(), INSIDE);
        started(tenantB, UUID.randomUUID(), INSIDE);

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(4);
    }

    @Test
    void theSameStudentActiveInTwoTenantsIsOnePlatformStudent() {
        UUID student = UUID.randomUUID();
        started(UUID.randomUUID(), student, INSIDE);
        started(UUID.randomUUID(), student, INSIDE.plusSeconds(60));

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(1);
    }

    @Test
    void theRangeIsHalfOpenOnStartedAt() {
        started(UUID.randomUUID(), UUID.randomUUID(), FROM); // lower bound included
        started(UUID.randomUUID(), UUID.randomUUID(), TO.minusSeconds(1));
        started(UUID.randomUUID(), UUID.randomUUID(), TO); // upper bound excluded
        started(UUID.randomUUID(), UUID.randomUUID(), FROM.minusSeconds(1)); // before

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(2);
    }

    @Test
    void adjacentWindowsPartitionTheStudentsAndTheSameStudentCountsInBoth() {
        UUID sticky = UUID.randomUUID();
        started(UUID.randomUUID(), sticky, PREVIOUS.plusSeconds(10)); // previous window
        started(UUID.randomUUID(), sticky, INSIDE); // and the current window
        started(UUID.randomUUID(), UUID.randomUUID(), FROM); // exactly on the shared boundary -> current only
        started(UUID.randomUUID(), UUID.randomUUID(), FROM.minusSeconds(1)); // previous only

        assertThat(service.countDistinctActiveStudentsPlatformWide(PREVIOUS, FROM)).isEqualTo(2);
        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(2);
    }

    @Test
    void softDeletedAttemptsAndNeverStartedAttemptsAreIgnored() {
        UUID tenant = UUID.randomUUID();
        attempt(tenant, UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, true); // deleted
        attempt(tenant, UUID.randomUUID(), AttemptStatus.CREATED, null, false); // never started
        UUID counted = UUID.randomUUID();
        started(tenant, counted, INSIDE);
        // a student whose only attempt is deleted does not count even if another student does
        UUID onlyDeleted = UUID.randomUUID();
        attempt(tenant, onlyDeleted, AttemptStatus.SUBMITTED, INSIDE, true);

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(1);
    }

    @Test
    void aStudentWithOneDeletedAndOneLiveAttemptInTheWindowCountsOnce() {
        UUID student = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        attempt(tenant, student, AttemptStatus.SUBMITTED, INSIDE, true);
        started(tenant, student, INSIDE.plusSeconds(5));

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(1);
    }

    @Test
    void statusDoesNotMatterOnlyThatTheAttemptWasStartedInTheWindow() {
        attempt(UUID.randomUUID(), UUID.randomUUID(), AttemptStatus.SUBMITTED, INSIDE, false);
        attempt(UUID.randomUUID(), UUID.randomUUID(), AttemptStatus.IN_PROGRESS, INSIDE, false);

        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(2);
    }

    @Test
    void noAttemptsMeansZeroAndAnEmptyOrInvertedRangeIsZero() {
        started(UUID.randomUUID(), UUID.randomUUID(), INSIDE);

        assertThat(service.countDistinctActiveStudentsPlatformWide(TO, TO)).isZero();
        assertThat(service.countDistinctActiveStudentsPlatformWide(TO, FROM)).isZero();
        assertThat(service.countDistinctActiveStudentsPlatformWide(Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-02-01T00:00:00Z"))).isZero();
    }

    @Test
    void thePlatformWideAndTenantScopedCountsAreIndependentQueries() {
        UUID tenant = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        started(tenant, shared, INSIDE);
        started(UUID.randomUUID(), shared, INSIDE);
        started(UUID.randomUUID(), UUID.randomUUID(), INSIDE);

        assertThat(service.countDistinctActiveStudents(tenant, FROM, TO)).isEqualTo(1);
        assertThat(service.countDistinctActiveStudentsPlatformWide(FROM, TO)).isEqualTo(2);
    }
}
