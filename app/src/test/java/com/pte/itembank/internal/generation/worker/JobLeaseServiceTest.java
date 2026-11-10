package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final JobLeaseService service = new JobLeaseService(jobs, items, properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final JobClaim claim = new JobClaim(5L, UUID.randomUUID(), UUID.randomUUID(), "MC_READING_SINGLE",
            CefrLevel.B2, QuestionPool.PRACTICE, null, "worker-1");

    private QuestionGenerationJob job(JobStatus status) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setId(5L);
        job.setPublicId(claim.jobPublicId());
        job.setRequestedByUserPublicId(claim.requesterId());
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setStatus(status);
        return job;
    }

    @Test
    void nothingIsClaimedWhenNoJobIsWaiting() {
        when(jobs.lockNextClaimable(NOW)).thenReturn(List.of());

        assertThat(service.claimNext("worker-1")).isEmpty();
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }

    @Test
    void aQueuedJobIsClaimedWithALeaseOfTheConfiguredLength() {
        properties.getWorker().setLeaseDuration(Duration.ofMinutes(10));
        when(jobs.lockNextClaimable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.QUEUED)));

        Optional<JobClaim> taken = service.claimNext("worker-1");

        assertThat(taken).isPresent();
        assertThat(taken.get().jobId()).isEqualTo(5L);
        assertThat(taken.get().workerId()).isEqualTo("worker-1");
        assertThat(taken.get().requesterId()).isEqualTo(claim.requesterId());
        verify(jobs).markRunning(5L, "worker-1", NOW.plus(Duration.ofMinutes(10)), NOW);
    }

    @Test
    void aRunningJobWhoseLeaseExpiredIsResumedByTheNewWorker() {
        when(jobs.lockNextClaimable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));

        assertThat(service.claimNext("worker-2")).isPresent();
        verify(jobs).markRunning(eq(5L), eq("worker-2"), any(), eq(NOW));
    }

    @Test
    void aCancelRequestWhoseWorkerDiedIsClosedNotResumed() {
        when(jobs.lockNextClaimable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.CANCELLING)));

        assertThat(service.claimNext("worker-2")).isEmpty();

        verify(items).cancelOpenUnleased(5L, NOW);
        verify(jobs).finishStaleCancelling(5L, NOW);
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }

    @Test
    void renewingExtendsTheLeaseFromNowAndReportsALostOne() {
        when(jobs.renewLease(5L, "worker-1", NOW.plus(properties.getWorker().getLeaseDuration()))).thenReturn(1);

        assertThat(service.renewLease(claim)).isTrue();

        when(jobs.renewLease(anyLong(), any(), any())).thenReturn(0);
        assertThat(service.renewLease(claim)).isFalse();
    }

    @Test
    void leaseStateDistinguishesHeldCancelRequestedAndLost() {
        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.RUNNING));
        assertThat(service.leaseState(claim)).isEqualTo(LeaseState.HELD);

        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.CANCELLING));
        assertThat(service.leaseState(claim)).isEqualTo(LeaseState.CANCEL_REQUESTED);

        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of());
        assertThat(service.leaseState(claim)).isEqualTo(LeaseState.LOST);
    }

    @Test
    void finishingWithoutAStatusCompletesWhenAtLeastOneQuestionWasSaved() {
        QuestionGenerationJob running = job(JobStatus.RUNNING);
        running.setSucceededCount(2);
        when(jobs.findById(5L)).thenReturn(Optional.of(running));

        service.finish(claim, null, null);

        verify(jobs).refreshCounters(5L);
        verify(jobs).finish(5L, "worker-1", JobStatus.COMPLETED, null, NOW);
    }

    @Test
    void finishingWithoutAStatusFailsWhenNothingWasSaved() {
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));

        service.finish(claim, null, null);

        verify(jobs).finish(5L, "worker-1", JobStatus.FAILED, null, NOW);
    }

    @Test
    void aPendingCancelRequestAlwaysWinsOverTheRequestedStatus() {
        QuestionGenerationJob cancelling = job(JobStatus.CANCELLING);
        cancelling.setSucceededCount(3);
        when(jobs.findById(5L)).thenReturn(Optional.of(cancelling));

        service.finish(claim, JobStatus.FAILED, "GENERATION_PROVIDER_UNAVAILABLE");

        verify(jobs).finish(5L, "worker-1", JobStatus.CANCELLED, "GENERATION_PROVIDER_UNAVAILABLE", NOW);
    }

    @Test
    void anExplicitStatusIsUsedWhenNoCancelIsPending() {
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));

        service.finish(claim, JobStatus.FAILED, "GENERATION_PROVIDER_AUTH");

        verify(jobs).finish(5L, "worker-1", JobStatus.FAILED, "GENERATION_PROVIDER_AUTH", NOW);
        verify(jobs, never()).finish(anyLong(), any(), eq(JobStatus.COMPLETED), isNull(), any());
    }
}
