package com.pte.admin.service;

import com.pte.admin.repository.ProjectionBackfillRepository;
import com.pte.admin.service.StudentRosterRebuildService.RebuildSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentRosterBackfillRunnerTest {

    private static final String NAME = StudentRosterBackfillRunner.BACKFILL_NAME;

    @Mock
    private StudentRosterRebuildService rebuildService;

    @Mock
    private ProjectionBackfillRepository backfillRepository;

    private StudentRosterBackfillRunner runner(int maxAttempts) {
        return new StudentRosterBackfillRunner(rebuildService, backfillRepository, true, maxAttempts,
                Duration.ZERO);
    }

    @Test
    void rebuilds_and_marks_completed_when_claim_is_won() {
        // Arrange
        when(backfillRepository.claim(eq(NAME), any(Instant.class))).thenReturn(1);
        when(rebuildService.rebuildAll()).thenReturn(new RebuildSummary(42, 100L));

        // Act
        runner(3).run();

        // Assert
        verify(rebuildService).rebuildAll();
        verify(backfillRepository).markCompleted(eq(NAME), any(Instant.class), eq(42));
        verify(backfillRepository, never()).releaseClaim(NAME);
    }

    @Test
    void skips_rebuild_entirely_when_another_instance_already_claimed() {
        // Arrange
        when(backfillRepository.claim(eq(NAME), any(Instant.class))).thenReturn(0);

        // Act
        runner(3).run();

        // Assert
        verifyNoInteractions(rebuildService);
        verify(backfillRepository, never()).markCompleted(any(), any(), anyInt());
    }

    @Test
    void retries_until_iam_becomes_reachable() {
        // Arrange: IAM is still booting for the first two attempts.
        when(backfillRepository.claim(eq(NAME), any(Instant.class))).thenReturn(1);
        when(rebuildService.rebuildAll())
                .thenThrow(new IllegalStateException("connection refused"))
                .thenThrow(new IllegalStateException("connection refused"))
                .thenReturn(new RebuildSummary(7, 50L));

        // Act
        runner(5).run();

        // Assert
        verify(rebuildService, times(3)).rebuildAll();
        verify(backfillRepository).markCompleted(eq(NAME), any(Instant.class), eq(7));
        verify(backfillRepository, never()).releaseClaim(NAME);
    }

    @Test
    void releases_claim_when_retries_are_exhausted_so_next_boot_retries() {
        // Arrange
        when(backfillRepository.claim(eq(NAME), any(Instant.class))).thenReturn(1);
        when(rebuildService.rebuildAll()).thenThrow(new IllegalStateException("connection refused"));

        // Act
        runner(3).run();

        // Assert
        verify(rebuildService, times(3)).rebuildAll();
        verify(backfillRepository).releaseClaim(NAME);
        verify(backfillRepository, never()).markCompleted(any(), any(), anyInt());
    }

    @Test
    void does_not_claim_anything_when_disabled() {
        // Arrange
        StudentRosterBackfillRunner disabled = new StudentRosterBackfillRunner(rebuildService,
                backfillRepository, false, 3, Duration.ZERO);

        // Act
        disabled.onApplicationReady();

        // Assert
        verifyNoInteractions(backfillRepository);
        verifyNoInteractions(rebuildService);
    }
}
