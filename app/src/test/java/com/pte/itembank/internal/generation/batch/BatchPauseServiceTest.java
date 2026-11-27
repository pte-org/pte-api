package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.worker.GenerationPauseRequested;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchPauseServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
    private static final long BATCH_ID = 7L;

    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final BatchChainingService chaining = mock(BatchChainingService.class);
    private final QuestionGenerationBatchQueryService queries = mock(QuestionGenerationBatchQueryService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BatchPauseService service = new BatchPauseService(jobs, batches, chaining, queries, audit, events,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final UUID publicId = UUID.randomUUID();
    private final QuestionGenerationBatch batch = new QuestionGenerationBatch();

    @BeforeEach
    void setUp() {
        batch.setId(BATCH_ID);
        batch.setPublicId(publicId);
        when(queries.require(publicId, owner)).thenReturn(batch);
        when(batches.findById(BATCH_ID)).thenReturn(Optional.of(batch));
    }

    private static QuestionGenerationJob child(JobStatus status) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setPublicId(UUID.randomUUID());
        job.setStatus(status);
        return job;
    }

    @Test
    void aPauseRequeuesTheWaitingChildrenAndInterruptsTheRunningOnesAfterLockingTheBatch() {
        QuestionGenerationJob running = child(JobStatus.RUNNING);
        QuestionGenerationJob pending = child(JobStatus.PENDING);
        when(batches.requestPause(BATCH_ID, NOW)).thenReturn(1);
        when(jobs.findByBatchIdOrderByBatchSeqAsc(BATCH_ID)).thenReturn(List.of(running, pending));
        when(jobs.markPausing(running.getPublicId())).thenReturn(1);
        batch.setStatus(BatchStatus.PAUSING);

        service.pause(publicId, owner);

        verify(batches).findByIdForUpdate(BATCH_ID);
        verify(jobs).requeueQueued(BATCH_ID);
        verify(jobs, never()).markPausing(pending.getPublicId());
        verify(events).publishEvent(new GenerationPauseRequested(running.getPublicId()));
        // Becomes PAUSED here only when no child holds a lease any more.
        verify(chaining).onChildPaused(BATCH_ID);
        verify(audit).record(eq(owner), eq(BatchGenerationConstants.BATCH_AGGREGATE), eq(publicId.toString()),
                eq(BatchGenerationConstants.BATCH_PAUSED), anyString());
    }

    @Test
    void aSecondPauseAnswersTheBatchAsItIs() {
        when(batches.requestPause(BATCH_ID, NOW)).thenReturn(0);
        batch.setStatus(BatchStatus.PAUSED);

        service.pause(publicId, owner);

        verify(queries).detail(BATCH_ID);
        verify(jobs, never()).requeueQueued(any());
        verify(audit, never()).record(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void aFinishedBatchCannotBePaused() {
        when(batches.requestPause(BATCH_ID, NOW)).thenReturn(0);
        batch.setStatus(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> service.pause(publicId, owner))
                .isInstanceOf(GenerationJobConflictException.class)
                .hasMessageContaining(BatchGenerationConstants.GENERATION_BATCH_NOT_PAUSABLE);
    }

    @Test
    void aResumeQueuesTheNextChildrenAndIsAudited() {
        when(batches.resume(BATCH_ID, NOW)).thenReturn(1);
        batch.setStatus(BatchStatus.RUNNING);

        service.resume(publicId, owner);

        verify(batches).findByIdForUpdate(BATCH_ID);
        verify(chaining).onResumed(BATCH_ID);
        verify(audit).record(eq(owner), eq(BatchGenerationConstants.BATCH_AGGREGATE), eq(publicId.toString()),
                eq(BatchGenerationConstants.BATCH_RESUMED), anyString());
    }

    @Test
    void aBatchStillStoppingItsChildrenCannotBeResumedYet() {
        when(batches.resume(BATCH_ID, NOW)).thenReturn(0);
        batch.setStatus(BatchStatus.PAUSING);

        assertThatThrownBy(() -> service.resume(publicId, owner))
                .isInstanceOf(GenerationJobConflictException.class)
                .hasMessageContaining(BatchGenerationConstants.GENERATION_BATCH_NOT_RESUMABLE);
        verify(chaining, never()).onResumed(BATCH_ID);
    }

    @Test
    void resumingARunningBatchAgainIsHarmless() {
        when(batches.resume(BATCH_ID, NOW)).thenReturn(0);
        batch.setStatus(BatchStatus.RUNNING);

        service.resume(publicId, owner);

        verify(queries).detail(BATCH_ID);
        verify(chaining, never()).onResumed(BATCH_ID);
    }
}
