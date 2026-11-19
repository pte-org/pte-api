package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchChainingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final long BATCH = 40L;
    private static final long JOB = 5L;

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final BatchChainingService service = new BatchChainingService(jobs, items, batches, meters,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private QuestionGenerationBatch batch(BatchStatus status, boolean cancelRequested, int succeeded, int total) {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(BATCH);
        batch.setStatus(status);
        batch.setCancelRequested(cancelRequested);
        batch.setSucceededCount(succeeded);
        batch.setTotalRequested(total);
        when(batches.findById(BATCH)).thenReturn(Optional.of(batch));
        when(batches.findByIdForUpdate(BATCH)).thenReturn(Optional.of(batch));
        return batch;
    }

    private void finishedChild(JobStatus status, String failureCode) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setId(JOB);
        job.setStatus(status);
        job.setFailureCode(failureCode);
        when(jobs.findById(JOB)).thenReturn(Optional.of(job));
    }

    @Test
    void aFinishedChildPromotesTheNextOne() {
        batch(BatchStatus.RUNNING, false, 0, 20);
        finishedChild(JobStatus.COMPLETED, null);
        when(jobs.promoteNext(BATCH)).thenReturn(1);

        service.onChildTerminal(BATCH, JOB);

        verify(batches).refreshCounters(BATCH, NOW);
        verify(batches, never()).close(anyLong(), any(), any(), any());
        verify(jobs, never()).cancelPending(anyLong(), any(), any());
    }

    @Test
    void theLastChildClosesTheBatchWithAStatusFromItsItems() {
        batch(BatchStatus.RUNNING, false, 7, 10);
        finishedChild(JobStatus.COMPLETED, null);

        service.onChildTerminal(BATCH, JOB);

        verify(batches).close(BATCH, BatchStatus.PARTIALLY_COMPLETED, null, NOW);
    }

    @Test
    void aCancelRequestStopsTheWaitingChildrenAndClosesCancelled() {
        batch(BatchStatus.CANCELLING, true, 3, 20);
        finishedChild(JobStatus.CANCELLED, null);

        service.onChildTerminal(BATCH, JOB);

        InOrder order = inOrder(items, jobs, batches);
        order.verify(items).cancelOpenUnleasedByBatch(BATCH, NOW);
        order.verify(jobs).cancelPending(BATCH, NOW, null);
        order.verify(batches).close(BATCH, BatchStatus.CANCELLED, null, NOW);
        verify(jobs, never()).promoteNext(anyLong());
    }

    @Test
    void aProviderAuthFailureStopsTheWholeBatch() {
        batch(BatchStatus.RUNNING, false, 0, 20);
        finishedChild(JobStatus.FAILED, "GENERATION_PROVIDER_AUTH");

        service.onChildTerminal(BATCH, JOB);

        verify(jobs).cancelPending(BATCH, NOW, "GENERATION_PROVIDER_AUTH");
        verify(batches).close(BATCH, BatchStatus.FAILED, "GENERATION_PROVIDER_AUTH", NOW);
        verify(jobs, never()).promoteNext(anyLong());
    }

    @Test
    void aRequesterNoLongerAllowedStopsTheBatchEvenThoughTheChildEndedCancelled() {
        batch(BatchStatus.RUNNING, false, 4, 20);
        finishedChild(JobStatus.CANCELLED, "GENERATION_REQUESTER_NOT_ALLOWED");

        service.onChildTerminal(BATCH, JOB);

        verify(jobs).cancelPending(BATCH, NOW, "GENERATION_REQUESTER_NOT_ALLOWED");
        verify(batches).close(BATCH, BatchStatus.PARTIALLY_COMPLETED, "GENERATION_REQUESTER_NOT_ALLOWED", NOW);
    }

    @Test
    void anOrdinaryItemFailureDoesNotStopTheBatch() {
        batch(BatchStatus.RUNNING, false, 0, 20);
        finishedChild(JobStatus.FAILED, "GENERATION_VALIDATION_FAILED");
        when(jobs.promoteNext(BATCH)).thenReturn(1);

        service.onChildTerminal(BATCH, JOB);

        verify(jobs, never()).cancelPending(anyLong(), any(), any());
    }

    @Test
    void theBatchStaysOpenWhileAChildIsStillOpen() {
        batch(BatchStatus.CANCELLING, true, 0, 20);
        finishedChild(JobStatus.CANCELLED, null);
        when(jobs.existsByBatchIdAndStatusIn(eq(BATCH), any())).thenReturn(true);

        service.onChildTerminal(BATCH, JOB);

        verify(batches, never()).close(anyLong(), any(), any(), any());
    }

    @Test
    void aHookFailureIsCountedAndRethrownSoTheFinishRollsBack() {
        batch(BatchStatus.RUNNING, false, 0, 20);
        finishedChild(JobStatus.COMPLETED, null);
        when(jobs.promoteNext(BATCH)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.onChildTerminal(BATCH, JOB)).isInstanceOf(IllegalStateException.class);
        assertThat(meters.counter("generation.batch.hook.failures").count()).isEqualTo(1.0);
    }

    @Test
    void reconcileClosesAnOpenBatchWithoutOpenChildren() {
        batch(BatchStatus.RUNNING, false, 10, 10);

        service.reconcile(BATCH);

        verify(batches).close(BATCH, BatchStatus.COMPLETED, null, NOW);
    }

    @Test
    void reconcileQueuesTheNextChildWhenOnlyWaitingChildrenRemain() {
        batch(BatchStatus.RUNNING, false, 0, 20);
        when(jobs.existsByBatchIdAndStatusIn(eq(BATCH), eq(JobStatus.ACTIVE))).thenReturn(false);
        when(jobs.promoteNext(BATCH)).thenReturn(1);

        service.reconcile(BATCH);

        verify(batches, never()).close(anyLong(), any(), any(), any());
    }

    @Test
    void reconcileLeavesAHealthyOrClosedBatchAlone() {
        batch(BatchStatus.COMPLETED, false, 10, 10);
        service.reconcile(BATCH);
        verify(jobs, never()).promoteNext(anyLong());

        batch(BatchStatus.RUNNING, false, 0, 20);
        when(jobs.existsByBatchIdAndStatusIn(eq(BATCH), any())).thenReturn(true);
        service.reconcile(BATCH);
        verify(jobs, never()).promoteNext(anyLong());
        verify(batches, never()).close(anyLong(), any(), isNull(), any());
    }

    @Test
    void reconcileOfACancelledBatchStopsItsWaitingChildren() {
        batch(BatchStatus.CANCELLING, true, 0, 20);

        service.reconcile(BATCH);

        verify(jobs).cancelPending(BATCH, NOW, null);
        verify(batches).close(BATCH, BatchStatus.CANCELLED, null, NOW);
    }
}
