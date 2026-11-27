package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.billing.BillingSettingsService;
import com.pte.itembank.internal.generation.batch.BatchChainingService;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.media.BatchPosition;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.util.Collection;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final GenerationLimits limits =
            new GenerationLimits(mock(BillingSettingsService.class, call -> call.getArgument(1)), properties);
    private final BatchChainingService chaining = mock(BatchChainingService.class);
    private final JobLeaseService service = new JobLeaseService(jobs, items, batches, limits, properties, chaining,
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

    private void queued(QuestionGenerationJob job, Long batchId) {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of());
        when(jobs.findNextQueued()).thenReturn(List.of(job.getId()));
        when(jobs.findBatchIdById(job.getId())).thenReturn(batchId == null ? List.of() : List.of(batchId));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobs.markRunning(eq(job.getId()), any(), any(), any())).thenReturn(1);
    }

    @Test
    void nothingIsClaimedWhenNoJobIsWaiting() {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of());
        when(jobs.findNextQueued()).thenReturn(List.of());

        assertThat(service.claimNext("worker-1")).isEmpty();
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }

    @Test
    void aQueuedJobIsClaimedWithALeaseOfTheConfiguredLength() {
        properties.getWorker().setLeaseDuration(Duration.ofMinutes(10));
        queued(job(JobStatus.QUEUED), null);

        Optional<JobClaim> taken = service.claimNext("worker-1");

        assertThat(taken).isPresent();
        assertThat(taken.get().jobId()).isEqualTo(5L);
        assertThat(taken.get().workerId()).isEqualTo("worker-1");
        assertThat(taken.get().requesterId()).isEqualTo(claim.requesterId());
        verify(jobs).markRunning(5L, "worker-1", NOW.plus(Duration.ofMinutes(10)), NOW);
        verify(batches, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void theClaimLockIsTakenBeforeAnyCandidateIsRead() {
        queued(job(JobStatus.QUEUED), null);

        service.claimNext("worker-1");

        InOrder order = inOrder(jobs);
        order.verify(jobs).lockClaims(QuestionGenerationConstants.CLAIM_ADVISORY_LOCK_KEY);
        order.verify(jobs).findNextResumable(NOW);
        order.verify(jobs).countLiveLeases(NOW);
        order.verify(jobs).findNextQueued();
    }

    @Test
    void atTheCapAQueuedJobWaits() {
        properties.getLimits().setMaxConcurrentJobs(2);
        queued(job(JobStatus.QUEUED), null);
        when(jobs.countLiveLeases(NOW)).thenReturn(2L);

        assertThat(service.claimNext("worker-1")).isEmpty();
        verify(jobs, never()).findNextQueued();
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }

    @Test
    void atTheCapAJobWhoseWorkerDiedIsStillResumed() {
        properties.getLimits().setMaxConcurrentJobs(1);
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobs.countLiveLeases(NOW)).thenReturn(1L);
        when(jobs.markRunning(eq(5L), eq("worker-2"), any(), eq(NOW))).thenReturn(1);

        assertThat(service.claimNext("worker-2")).isPresent();
        verify(jobs, never()).countLiveLeases(any());
    }

    @Test
    void aBatchChildLocksItsBatchBeforeTheJobIsMarkedRunning() {
        QuestionGenerationJob child = job(JobStatus.QUEUED);
        child.setBatchId(40L);
        queued(child, 40L);

        assertThat(service.claimNext("worker-1")).isPresent();

        InOrder order = inOrder(batches, jobs);
        order.verify(batches).findByIdForUpdate(40L);
        order.verify(jobs).markRunning(eq(5L), eq("worker-1"), any(), eq(NOW));
        order.verify(batches).markRunning(40L, NOW);
    }

    @Test
    void aBatchChildCarriesItsPlaceAmongTheBatchsSpokenQuestions() {
        UUID batchPublicId = UUID.randomUUID();
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(batchPublicId);
        when(batches.findById(40L)).thenReturn(Optional.of(batch));
        when(jobs.sumRequestedCountBefore(eq(40L), eq(2), any())).thenReturn(8L);
        QuestionGenerationJob child = job(JobStatus.QUEUED);
        child.setBatchId(40L);
        child.setBatchSeq(2);
        queued(child, 40L);

        BatchPosition position = service.claimNext("worker-1").orElseThrow().batchPosition();

        assertThat(position.offset()).isEqualTo(8);
        assertThat(position.seed()).isEqualTo(batchPublicId.getLeastSignificantBits());
    }

    @Test
    void aResumedBatchChildKeepsItsPlace() {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(UUID.randomUUID());
        when(batches.findById(40L)).thenReturn(Optional.of(batch));
        when(jobs.sumRequestedCountBefore(eq(40L), eq(3), any())).thenReturn(11L);
        QuestionGenerationJob running = job(JobStatus.RUNNING);
        running.setBatchId(40L);
        running.setBatchSeq(3);
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.findById(5L)).thenReturn(Optional.of(running));
        when(jobs.markRunning(eq(5L), any(), any(), any())).thenReturn(1);

        assertThat(service.claimNext("worker-2").orElseThrow().batchPosition().offset()).isEqualTo(11);
    }

    @Test
    void onlyTaskTypesThatAreSpokenTakeAPlaceInTheSequence() {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(UUID.randomUUID());
        when(batches.findById(40L)).thenReturn(Optional.of(batch));
        QuestionGenerationJob child = job(JobStatus.QUEUED);
        child.setBatchId(40L);
        child.setBatchSeq(1);
        queued(child, 40L);

        service.claimNext("worker-1");

        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.captor();
        verify(jobs).sumRequestedCountBefore(eq(40L), eq(1), keys.capture());
        assertThat(keys.getValue()).contains("REPEAT_SENTENCE", "RE_TELL_LECTURE")
                .doesNotContain("MC_READING_SINGLE", "DESCRIBE_IMAGE", "READ_ALOUD");
    }

    @Test
    void aJobOutsideAnyBatchHasNoPlace() {
        QuestionGenerationJob standalone = job(JobStatus.QUEUED);
        queued(standalone, null);

        assertThat(service.claimNext("worker-1").orElseThrow().batchPosition()).isNull();
    }

    @Test
    void aCancelThatWonSinceTheSelectLeavesNothingClaimed() {
        QuestionGenerationJob child = job(JobStatus.QUEUED);
        queued(child, 40L);
        when(jobs.markRunning(anyLong(), any(), any(), any())).thenReturn(0);

        assertThat(service.claimNext("worker-1")).isEmpty();
        verify(batches, never()).markRunning(anyLong(), any());
    }

    @Test
    void aJobNoLongerQueuedAfterTheBatchLockIsNotClaimed() {
        queued(job(JobStatus.CANCELLED), 40L);

        assertThat(service.claimNext("worker-1")).isEmpty();
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }

    @Test
    void aRunningJobWhoseLeaseExpiredIsResumedByTheNewWorker() {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobs.markRunning(anyLong(), any(), any(), any())).thenReturn(1);

        assertThat(service.claimNext("worker-2")).isPresent();
        verify(jobs).markRunning(eq(5L), eq("worker-2"), any(), eq(NOW));
    }

    @Test
    void aCancelRequestWhoseWorkerDiedIsClosedNotResumed() {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
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

        when(jobs.statusIfLeaseHeld(5L, "worker-1")).thenReturn(List.of(JobStatus.PAUSING));
        assertThat(service.leaseState(claim)).isEqualTo(LeaseState.PAUSE_REQUESTED);

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

    @Test
    void aBatchChildLocksItsBatchBeforeFinishingAndThenChains() {
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobs.finish(anyLong(), any(), any(), any(), any())).thenReturn(1);

        service.finish(claim, JobStatus.FAILED, "GENERATION_PROVIDER_AUTH");

        InOrder order = inOrder(batches, jobs, chaining);
        order.verify(batches).findByIdForUpdate(40L);
        order.verify(jobs).finish(5L, "worker-1", JobStatus.FAILED, "GENERATION_PROVIDER_AUTH", NOW);
        order.verify(chaining).onChildTerminal(40L, 5L);
    }

    @Test
    void aFinishThatLostItsLeaseNeverChains() {
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobs.finish(anyLong(), any(), any(), any(), any())).thenReturn(0);

        service.finish(claim, null, null);

        verify(chaining, never()).onChildTerminal(anyLong(), anyLong());
    }

    @Test
    void aStandaloneJobFinishesWithoutTouchingBatches() {
        when(jobs.findBatchIdById(5L)).thenReturn(List.of());
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.RUNNING)));
        when(jobs.finish(anyLong(), any(), any(), any(), any())).thenReturn(1);

        service.finish(claim, null, null);

        verify(batches, never()).findByIdForUpdate(anyLong());
        verify(chaining, never()).onChildTerminal(anyLong(), anyLong());
    }

    @Test
    void aStaleCancelOfABatchChildLocksTheBatchFirstAndChains() {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.CANCELLING)));
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.finishStaleCancelling(5L, NOW)).thenReturn(1);

        assertThat(service.claimNext("worker-2")).isEmpty();

        InOrder order = inOrder(batches, jobs, chaining);
        order.verify(batches).findByIdForUpdate(40L);
        order.verify(jobs).finishStaleCancelling(5L, NOW);
        order.verify(chaining).onChildTerminal(40L, 5L);
    }

    @Test
    void aReleasedPausedChildRequeuesItsQuestionsAndLetsItsBatchSettle() {
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.release(5L, "worker-1")).thenReturn(1);

        service.release(claim);

        InOrder order = inOrder(batches, items, jobs, chaining);
        order.verify(batches).findByIdForUpdate(40L);
        order.verify(items).requeueOpen(5L, NOW);
        order.verify(jobs).release(5L, "worker-1");
        order.verify(chaining).onChildPaused(40L);
        verify(jobs, never()).finish(anyLong(), any(), any(), any(), any());
    }

    @Test
    void aReleaseThatLostItsLeaseLeavesTheBatchAlone() {
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.release(5L, "worker-1")).thenReturn(0);

        service.release(claim);

        verify(chaining, never()).onChildPaused(anyLong());
    }

    @Test
    void aPauseWhoseWorkerDiedGoesBackToWaitingNotRunning() {
        when(jobs.findNextResumable(NOW)).thenReturn(List.of(5L));
        when(jobs.findById(5L)).thenReturn(Optional.of(job(JobStatus.PAUSING)));
        when(jobs.findBatchIdById(5L)).thenReturn(List.of(40L));
        when(jobs.releaseStalePausing(5L)).thenReturn(1);

        assertThat(service.claimNext("worker-2")).isEmpty();

        verify(items).requeueOpen(5L, NOW);
        verify(chaining).onChildPaused(40L);
        verify(jobs, never()).markRunning(anyLong(), any(), any(), any());
    }
}
