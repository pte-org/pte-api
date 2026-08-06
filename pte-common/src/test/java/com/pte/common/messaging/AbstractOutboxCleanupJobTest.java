package com.pte.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AbstractOutboxCleanupJob}'s {@code cutoff()} boundary
 * logic and its {@code cleanup()} -> repository delegation. Package-private
 * {@code cutoff()} is only reachable from a test in the same package.
 */
class AbstractOutboxCleanupJobTest {

    /** Minimal concrete outbox entity, only needed to satisfy the generic bound. */
    static class TestOutboxEntry extends AbstractOutboxEntry {
    }

    static class TestCleanupJob extends AbstractOutboxCleanupJob<TestOutboxEntry> {
        TestCleanupJob(OutboxJpaRepository<TestOutboxEntry> repository) {
            super(repository);
        }
    }

    @SuppressWarnings("unchecked")
    private final OutboxJpaRepository<TestOutboxEntry> repository = mock(OutboxJpaRepository.class);

    @Test
    void cutoff_subtractsConfiguredRetentionDaysFromNow() {
        TestCleanupJob job = new TestCleanupJob(repository);
        ReflectionTestUtils.setField(job, "retentionDays", 7);

        Instant before = Instant.now();
        Instant cutoff = job.cutoff();
        Instant after = Instant.now();

        assertThat(cutoff).isBetween(before.minus(7, ChronoUnit.DAYS).minusSeconds(2),
                after.minus(7, ChronoUnit.DAYS).plusSeconds(2));
    }

    @Test
    void cutoff_zeroRetentionDays_isEffectivelyNow() {
        TestCleanupJob job = new TestCleanupJob(repository);
        ReflectionTestUtils.setField(job, "retentionDays", 0);

        Instant before = Instant.now();
        Instant cutoff = job.cutoff();
        Instant after = Instant.now();

        assertThat(cutoff).isBetween(before, after);
    }

    @Test
    void cleanup_delegatesToRepositoryWithComputedCutoff() {
        TestCleanupJob job = new TestCleanupJob(repository);
        ReflectionTestUtils.setField(job, "retentionDays", 7);
        when(repository.deletePublishedBefore(any())).thenReturn(3);

        job.cleanup();

        verify(repository).deletePublishedBefore(any(Instant.class));
    }

    @Test
    void cleanup_scheduledAnnotation_pinsCronToUtcZone() throws NoSuchMethodException {
        // postgres-utc-timestamptz-migration Phase 1: once the JVM default timezone is
        // forced to UTC, an unpinned cron expression would silently shift its real-world
        // fire time (was ~3am Vietnam-local, would become 3am UTC = 10am Vietnam without
        // this). Verifies the annotation is pinned, not the scheduler's actual trigger
        // behavior (which needs a running scheduler, out of scope for a unit test).
        Method cleanupMethod = AbstractOutboxCleanupJob.class.getDeclaredMethod("cleanup");
        Scheduled scheduled = cleanupMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("UTC");
    }

    @Test
    void cleanup_passesCutoffMatchingRetentionWindow() {
        TestCleanupJob job = new TestCleanupJob(repository);
        ReflectionTestUtils.setField(job, "retentionDays", 30);
        when(repository.deletePublishedBefore(any())).thenReturn(0);

        Instant before = Instant.now();
        job.cleanup();
        Instant after = Instant.now();

        org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).deletePublishedBefore(captor.capture());

        assertThat(captor.getValue()).isBetween(before.minus(30, ChronoUnit.DAYS).minusSeconds(2),
                after.minus(30, ChronoUnit.DAYS).plusSeconds(2));
    }
}
