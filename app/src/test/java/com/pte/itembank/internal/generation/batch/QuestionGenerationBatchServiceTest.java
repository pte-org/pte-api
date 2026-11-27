package com.pte.itembank.internal.generation.batch;

import com.pte.billing.BillingSettingsService;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.batch.BatchRequestValidator.ValidatedBatch;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.batch.dto.SubmitGenerationBatchRequest;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.job.GenerationQuotaGuard;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.exception.GenerationBatchNotFoundException;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.worker.GenerationCancelRequested;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-11-17T00:00:00Z");

    private final BatchRequestValidator validator = mock(BatchRequestValidator.class);
    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final QuestionGenerationProperties properties = new QuestionGenerationProperties();
    private final QuestionGenerationBatchQueryService queries = mock(QuestionGenerationBatchQueryService.class);
    private final GenerationLimits limits = new GenerationLimits(mock(BillingSettingsService.class, call -> call.getArgument(1)), properties);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final QuestionGenerationBatchService service = new QuestionGenerationBatchService(validator, jobs, items,
            batches, limits, audit, queries,
            new GenerationQuotaGuard(jobs, limits, Clock.fixed(NOW, ZoneOffset.UTC)), events,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final SubmitGenerationBatchRequest request = new SubmitGenerationBatchRequest("PRACTICE", List.of());

    @BeforeEach
    void setUp() {
        // One child at a time unless a test says otherwise, so the run order stays easy to read.
        properties.getLimits().setMaxConcurrentJobs(1);
        BatchRow first = new BatchRow("MC_READING_SINGLE", CefrLevel.B2, 12, List.of(), ImageKind.AUTO);
        BatchRow second = new BatchRow("WRITE_ESSAY", CefrLevel.C1, 3, List.of("climate"), ImageKind.AUTO);
        when(validator.validate(request, staff))
                .thenReturn(new ValidatedBatch(QuestionPool.PRACTICE, List.of(first, second), 15));
        when(batches.saveAndFlush(any())).thenAnswer(call -> {
            QuestionGenerationBatch batch = call.getArgument(0);
            batch.setId(40L);
            batch.setPublicId(UUID.randomUUID());
            return batch;
        });
        AtomicLong ids = new AtomicLong(100);
        when(jobs.saveAllAndFlush(any())).thenAnswer(call -> {
            List<QuestionGenerationJob> children = call.getArgument(0);
            children.forEach(child -> {
                child.setId(ids.getAndIncrement());
                child.setPublicId(UUID.randomUUID());
            });
            return children;
        });
    }

    @Test
    void submitCreatesTheBatchAndAllChildrenWithOnlyTheFirstClaimable() {
        GenerationBatchResponse response = service.submit(request, staff);

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.totalRequested()).isEqualTo(15);
        assertThat(response.openCount()).isEqualTo(15);
        assertThat(response.children()).extracting("status").containsExactly("QUEUED", "PENDING", "PENDING");
        assertThat(response.children()).extracting("requestedCount").containsExactly(10, 2, 3);
        assertThat(response.currentJobPublicId()).isEqualTo(response.children().get(0).jobPublicId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionGenerationJob>> children = ArgumentCaptor.forClass(List.class);
        verify(jobs).saveAllAndFlush(children.capture());
        assertThat(children.getValue()).allSatisfy(child -> {
            assertThat(child.getBatchId()).isEqualTo(40L);
            assertThat(child.isSaveQuestions()).isTrue();
            assertThat(child.getRequestedByUserPublicId()).isEqualTo(staff.userId());
        });
        assertThat(children.getValue().get(2).getTopicHint()).isEqualTo("climate");
        assertThat(children.getValue().get(2).getBatchRowIndex()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionGenerationJobItem>> created = ArgumentCaptor.forClass(List.class);
        verify(items).saveAll(created.capture());
        assertThat(created.getValue()).hasSize(15);
        verify(audit).record(eq(staff), eq("QUESTION_GENERATION_BATCH"), anyString(),
                eq("QUESTION_GENERATION_BATCH_CREATED"), eq("rows=2,items=15,children=3"));
    }

    @Test
    void theSubmitLockIsTakenAfterValidationAndBeforeTheChecks() {
        service.submit(request, staff);

        InOrder order = inOrder(validator, jobs, batches);
        order.verify(validator).validate(request, staff);
        order.verify(jobs).lockSubmissions(anyLong());
        order.verify(batches).existsByRequestedByUserPublicIdAndStatusIn(eq(staff.userId()), any());
        order.verify(batches).saveAndFlush(any());
    }

    @Test
    void anOpenSavingJobBlocksABatch() {
        when(jobs.existsByRequestedByUserPublicIdAndSaveQuestionsTrueAndStatusIn(eq(staff.userId()), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationJobConflictException.class)
                .extracting("code").isEqualTo("GENERATION_JOB_ALREADY_RUNNING");
        verify(batches, never()).saveAndFlush(any());
    }

    @Test
    void anOpenBatchBlocksASecondBatch() {
        when(batches.existsByRequestedByUserPublicIdAndStatusIn(eq(staff.userId()), any())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationJobConflictException.class);
    }

    @Test
    void theWholeBatchIsCountedAgainstTheUserAndGlobalQuota() {
        when(jobs.sumRequestedSinceByUser(staff.userId(), DAY_START)).thenReturn(86L);
        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationLimitException.class)
                .extracting("code").isEqualTo("GENERATION_DAILY_LIMIT_REACHED");

        when(jobs.sumRequestedSinceByUser(staff.userId(), DAY_START)).thenReturn(85L);
        when(jobs.sumRequestedSince(DAY_START)).thenReturn(286L);
        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationLimitException.class)
                .extracting("code").isEqualTo("GENERATION_GLOBAL_LIMIT_REACHED");

        when(jobs.sumRequestedSince(DAY_START)).thenReturn(285L);
        assertThat(service.submit(request, staff).totalRequested()).isEqualTo(15);
    }

    @Test
    void regressionAUserLimitOfFiveRejectsTheBatchAndZeroLimitsAcceptIt() {
        properties.getLimits().setMaxItemsPerUserPerDay(5);
        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationLimitException.class)
                .extracting("code").isEqualTo("GENERATION_DAILY_LIMIT_REACHED");

        properties.getLimits().setMaxItemsPerUserPerDay(0);
        properties.getLimits().setMaxItemsPerDayGlobal(0);
        when(jobs.sumRequestedSinceByUser(staff.userId(), DAY_START)).thenReturn(10_000L);
        when(jobs.sumRequestedSince(DAY_START)).thenReturn(100_000L);
        assertThat(service.submit(request, staff).totalRequested()).isEqualTo(15);
    }

    @Test
    void aRaceLostToTheUniqueIndexIsTheSameConflict() {
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate")).when(batches).saveAndFlush(any());

        assertThatThrownBy(() -> service.submit(request, staff)).isInstanceOf(GenerationJobConflictException.class);
        verify(items, never()).saveAll(any());
    }

    @Test
    void asManyChildrenAreClaimableAsGenerationRunsInParallel() {
        properties.getLimits().setMaxConcurrentJobs(2);

        service.submit(request, staff);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionGenerationJob>> children = ArgumentCaptor.forClass(List.class);
        verify(jobs).saveAllAndFlush(children.capture());
        assertThat(children.getValue()).extracting(QuestionGenerationJob::getStatus)
                .containsExactly(JobStatus.QUEUED, JobStatus.QUEUED, JobStatus.PENDING);
    }

    @Test
    void childrenWaitPendingExceptTheFirst() {
        service.submit(request, staff);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuestionGenerationJob>> children = ArgumentCaptor.forClass(List.class);
        verify(jobs).saveAllAndFlush(children.capture());
        assertThat(children.getValue()).extracting(QuestionGenerationJob::getStatus)
                .containsExactly(JobStatus.QUEUED, JobStatus.PENDING, JobStatus.PENDING);
        assertThat(children.getValue()).extracting(QuestionGenerationJob::getBatchSeq).containsExactly(0, 1, 2);
    }

    private QuestionGenerationBatch existing(BatchStatus status) {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(UUID.randomUUID());
        batch.setRequestedByUserPublicId(staff.userId());
        batch.setStatus(status);
        when(queries.require(batch.getPublicId(), staff)).thenReturn(batch);
        when(batches.findById(40L)).thenReturn(java.util.Optional.of(batch));
        return batch;
    }

    @Test
    void cancellingStopsTheWaitingChildrenFirstThenAsksTheRunningOneToStop() {
        QuestionGenerationBatch batch = existing(BatchStatus.RUNNING);
        when(batches.requestCancel(40L, NOW)).thenReturn(1);
        QuestionGenerationJob running = new QuestionGenerationJob();
        running.setPublicId(UUID.randomUUID());
        running.setStatus(JobStatus.RUNNING);
        QuestionGenerationJob cancelled = new QuestionGenerationJob();
        cancelled.setPublicId(UUID.randomUUID());
        cancelled.setStatus(JobStatus.CANCELLED);
        when(jobs.findByBatchIdOrderByBatchSeqAsc(40L)).thenReturn(List.of(running, cancelled));
        when(jobs.existsByBatchIdAndStatusIn(eq(40L), any())).thenReturn(true);
        when(jobs.markCancelling(running.getPublicId())).thenReturn(1);

        service.cancel(batch.getPublicId(), staff);
        verify(events).publishEvent(new GenerationCancelRequested(running.getPublicId()));

        InOrder order = inOrder(batches, items, jobs);
        order.verify(batches).findByIdForUpdate(40L);
        order.verify(batches).requestCancel(40L, NOW);
        order.verify(items).cancelOpenUnleasedByBatch(40L, NOW);
        order.verify(jobs).cancelPending(40L, NOW, null);
        order.verify(jobs).markCancelling(running.getPublicId());
        verify(jobs, never()).markCancelling(cancelled.getPublicId());
        verify(batches, never()).close(anyLong(), any(), any(), any());
        verify(audit).record(eq(staff), eq("QUESTION_GENERATION_BATCH"), eq(batch.getPublicId().toString()),
                eq("QUESTION_GENERATION_BATCH_CANCELLED"), anyString());
    }

    @Test
    void aQueuedBatchWithNothingRunningIsClosedAtOnce() {
        QuestionGenerationBatch batch = existing(BatchStatus.QUEUED);
        when(batches.requestCancel(40L, NOW)).thenReturn(1);
        when(jobs.findByBatchIdOrderByBatchSeqAsc(40L)).thenReturn(List.of());

        service.cancel(batch.getPublicId(), staff);

        verify(batches).close(40L, BatchStatus.CANCELLED, null, NOW);
    }

    @Test
    void cancellingTwiceIsANoOp() {
        QuestionGenerationBatch batch = existing(BatchStatus.CANCELLING);

        service.cancel(batch.getPublicId(), staff);
        batch.setStatus(BatchStatus.CANCELLED);
        service.cancel(batch.getPublicId(), staff);

        verify(jobs, never()).cancelPending(anyLong(), any(), any());
        verify(queries, org.mockito.Mockito.times(2)).detail(40L);
    }

    @Test
    void aFinishedBatchCannotBeCancelled() {
        QuestionGenerationBatch batch = existing(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> service.cancel(batch.getPublicId(), staff))
                .isInstanceOf(GenerationJobConflictException.class)
                .extracting("code").isEqualTo("GENERATION_BATCH_NOT_CANCELLABLE");
    }

    @Test
    void anotherWritersBatchCannotBeCancelled() {
        UUID other = UUID.randomUUID();
        when(queries.require(other, staff)).thenThrow(new GenerationBatchNotFoundException());

        assertThatThrownBy(() -> service.cancel(other, staff)).isInstanceOf(GenerationBatchNotFoundException.class);
        verify(batches, never()).requestCancel(anyLong(), any());
    }
}
