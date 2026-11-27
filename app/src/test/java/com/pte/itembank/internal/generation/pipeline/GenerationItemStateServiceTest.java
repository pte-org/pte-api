package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationItemStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationItemStateService service = new GenerationItemStateService(items, jobs,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "READ_ALOUD",
            CefrLevel.B2, QuestionPool.PRACTICE, null, "worker-1");

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void aStoppedThreadDoesNotMarkTheQuestionFailed() {
        Thread.currentThread().interrupt(); // a cancel or a pause interrupted the provider call

        service.fail(claim, 0, "GENERATION_PROVIDER_UNAVAILABLE", null);

        verify(items, never()).fail(anyLong(), anyInt(), anyString(), any(), anyString(), any());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void aRealFailureIsStillRecorded() {
        service.fail(claim, 0, "GENERATION_PROVIDER_UNAVAILABLE", null);

        verify(items).fail(5L, 0, "GENERATION_PROVIDER_UNAVAILABLE", null, "worker-1", NOW);
    }

    @Test
    void aCancelWinsOverAPauseWhenTheReasonIsReadBack() {
        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.CANCELLING));
        assertThat(service.stopRequested(claim)).isEqualTo(ItemOutcome.CANCELLED);

        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.PAUSING));
        assertThat(service.stopRequested(claim)).isEqualTo(ItemOutcome.PAUSED);

        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.RUNNING));
        assertThat(service.stopRequested(claim)).isNull();
        assertThat(service.cancelRequested(claim)).isFalse();
    }
}
