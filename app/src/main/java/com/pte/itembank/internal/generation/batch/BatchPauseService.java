package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.worker.GenerationPauseRequested;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pauses and resumes a batch. A pause stops at once: children queued but not claimed wait again, and every running
 * child is interrupted (its worker hands it back to waiting, its unfinished question starting over later). The batch
 * is PAUSING until none of its children holds a lease, then PAUSED. Both sides lock the batch row first, then the
 * jobs, like the cancel and the worker, so they cannot deadlock. A paused batch stays open (one open batch each).
 */
@Service
public class BatchPauseService {

    private final GenerationJobRepository jobs;
    private final GenerationBatchRepository batches;
    private final BatchChainingService chaining;
    private final QuestionGenerationBatchQueryService queries;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BatchPauseService(GenerationJobRepository jobs, GenerationBatchRepository batches,
                             BatchChainingService chaining, QuestionGenerationBatchQueryService queries,
                             AuditLogService audit, ApplicationEventPublisher events, Clock clock) {
        this.jobs = jobs;
        this.batches = batches;
        this.chaining = chaining;
        this.queries = queries;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Idempotent: a batch already pausing or paused answers its current state. */
    @Transactional
    public GenerationBatchResponse pause(UUID publicId, CurrentUser caller) {
        Long batchId = queries.require(publicId, caller).getId();
        batches.findByIdForUpdate(batchId);
        Instant now = clock.instant();
        if (batches.requestPause(batchId, now) == 0) {
            if (BatchStatus.HALTED.contains(statusOf(batchId))) {
                return queries.detail(batchId);
            }
            throw new GenerationJobConflictException(BatchGenerationConstants.GENERATION_BATCH_NOT_PAUSABLE,
                    BatchGenerationConstants.GENERATION_BATCH_NOT_PAUSABLE_MESSAGE);
        }
        jobs.requeueQueued(batchId);
        jobs.findByBatchIdOrderByBatchSeqAsc(batchId).stream()
                .filter(child -> child.getStatus() == JobStatus.RUNNING)
                .filter(child -> jobs.markPausing(child.getPublicId()) == 1)
                .forEach(child -> events.publishEvent(new GenerationPauseRequested(child.getPublicId())));
        chaining.onChildPaused(batchId);
        record(caller, publicId, batchId, BatchGenerationConstants.BATCH_PAUSED);
        return queries.detail(batchId);
    }

    /**
     * Idempotent for a batch already running again. A batch still stopping its children cannot be resumed yet: its
     * interrupted children have not been handed back, so they could not be queued in their order.
     */
    @Transactional
    public GenerationBatchResponse resume(UUID publicId, CurrentUser caller) {
        Long batchId = queries.require(publicId, caller).getId();
        batches.findByIdForUpdate(batchId);
        if (batches.resume(batchId, clock.instant()) == 0) {
            BatchStatus current = statusOf(batchId);
            if (current == BatchStatus.QUEUED || current == BatchStatus.RUNNING) {
                return queries.detail(batchId);
            }
            throw new GenerationJobConflictException(BatchGenerationConstants.GENERATION_BATCH_NOT_RESUMABLE,
                    BatchGenerationConstants.GENERATION_BATCH_NOT_RESUMABLE_MESSAGE);
        }
        chaining.onResumed(batchId);
        record(caller, publicId, batchId, BatchGenerationConstants.BATCH_RESUMED);
        return queries.detail(batchId);
    }

    private BatchStatus statusOf(long batchId) {
        return batches.findById(batchId).orElseThrow().getStatus();
    }

    private void record(CurrentUser caller, UUID publicId, long batchId, String action) {
        audit.record(caller, BatchGenerationConstants.BATCH_AGGREGATE, publicId.toString(), action,
                String.format(BatchGenerationConstants.BATCH_PAUSE_SUMMARY, statusOf(batchId)));
    }
}
