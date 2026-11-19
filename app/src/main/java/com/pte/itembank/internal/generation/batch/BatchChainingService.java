package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * What happens to a batch when one of its children ends: the next child is promoted, or the batch is stopped (cancel
 * requested, or a fatal failure every later child would repeat), and a batch without open children is closed with a
 * status recomputed from its items. Callers hold the batch row lock (batch first, then jobs) and run in their own
 * transaction, so the child's terminal update and the chaining commit or roll back together.
 */
@Service
public class BatchChainingService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchChainingService.class);

    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationBatchRepository batches;
    private final MeterRegistry meters;
    private final Clock clock;

    public BatchChainingService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                                GenerationBatchRepository batches, MeterRegistry meters, Clock clock) {
        this.jobs = jobs;
        this.items = items;
        this.batches = batches;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Called once per child terminal transition, after the guarded update succeeded. A failure rolls back that
     * transition too: the child stays leased, its lease expires and it is resumed and finished again; the
     * reconciler is the last resort.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onChildTerminal(long batchId, long jobId) {
        try {
            chain(batchId, jobId);
        } catch (RuntimeException failure) {
            LOG.warn("Batch chaining failed: batchId={}, jobId={}, errorType={}", batchId, jobId,
                    failure.getClass().getSimpleName());
            meters.counter(BatchGenerationConstants.HOOK_FAILURE_METRIC).increment();
            throw failure;
        }
    }

    /**
     * Safety net for a missed hook: closes an open batch without open children, stops the waiting children of a
     * cancelled batch, and queues the next child of a batch whose promotion was lost. Its own short transaction.
     */
    @Transactional
    public void reconcile(long batchId) {
        QuestionGenerationBatch batch = batches.findByIdForUpdate(batchId).orElse(null);
        if (batch == null || !BatchStatus.OPEN.contains(batch.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        if (batch.isCancelRequested()) {
            stopWaitingChildren(batchId, null, now);
        } else if (!jobs.existsByBatchIdAndStatusIn(batchId, JobStatus.ACTIVE) && jobs.promoteNext(batchId) == 1) {
            LOG.warn("Batch repaired, next child queued: batchId={}", batchId);
            return;
        }
        if (closeIfDone(batchId, null, now)) {
            LOG.warn("Batch repaired, closed: batchId={}", batchId);
        }
    }

    private void chain(long batchId, long jobId) {
        Instant now = clock.instant();
        QuestionGenerationJob job = jobs.findById(jobId).orElseThrow();
        QuestionGenerationBatch batch = batches.findById(batchId).orElseThrow();
        // Fatal regardless of the child's status: REQUESTER_NOT_ALLOWED ends the child CANCELLED, not FAILED.
        // Null-checked first: Set.of rejects contains(null).
        String fatalCode = job.getFailureCode() != null
                && BatchGenerationConstants.FATAL_FAILURE_CODES.contains(job.getFailureCode())
                ? job.getFailureCode() : null;
        if (batch.isCancelRequested() || fatalCode != null) {
            stopWaitingChildren(batchId, fatalCode, now);
        } else if (jobs.promoteNext(batchId) == 1) {
            batches.refreshCounters(batchId, now);
            return;
        }
        closeIfDone(batchId, fatalCode, now);
    }

    /** Items first: they are selected through the status of their (still waiting) child. */
    private void stopWaitingChildren(long batchId, String fatalCode, Instant now) {
        items.cancelOpenUnleasedByBatch(batchId, now);
        jobs.cancelPending(batchId, now, fatalCode);
    }

    private boolean closeIfDone(long batchId, String fatalCode, Instant now) {
        batches.refreshCounters(batchId, now);
        if (jobs.existsByBatchIdAndStatusIn(batchId, JobStatus.OPEN)) {
            return false;
        }
        QuestionGenerationBatch counted = batches.findById(batchId).orElseThrow();
        BatchStatus status = BatchStatusCalculator.of(counted.isCancelRequested(), counted.getSucceededCount(),
                counted.getTotalRequested());
        return batches.close(batchId, status, fatalCode, now) == 1;
    }
}
