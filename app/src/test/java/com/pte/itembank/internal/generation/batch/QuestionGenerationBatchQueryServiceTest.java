package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.exception.GenerationBatchNotFoundException;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationBatchQueryServiceTest {

    private final GenerationBatchRepository batches = mock(GenerationBatchRepository.class);
    private final GenerationJobRepository jobs = mock(GenerationJobRepository.class);
    private final GenerationJobItemRepository items = mock(GenerationJobItemRepository.class);
    private final QuestionGenerationBatchQueryService service =
            new QuestionGenerationBatchQueryService(batches, jobs, items,
                    org.mockito.Mockito.mock(com.pte.itembank.internal.generation.config.GenerationLimits.class));
    private final CurrentUser staffA = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final CurrentUser staffB = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private final CurrentUser manager = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));

    private QuestionGenerationBatch batchOf(CurrentUser owner) {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setId(40L);
        batch.setPublicId(UUID.randomUUID());
        batch.setRequestedByUserPublicId(owner.userId());
        batch.setPool(QuestionPool.PRACTICE);
        batch.setStatus(BatchStatus.RUNNING);
        batch.setTotalRequested(20);
        when(batches.findByPublicIdAndDeletedFalse(batch.getPublicId())).thenReturn(Optional.of(batch));
        when(batches.findById(40L)).thenReturn(Optional.of(batch));
        return batch;
    }

    private static QuestionGenerationJob child(int seq, JobStatus status) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setPublicId(UUID.randomUUID());
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setBatchSeq(seq);
        job.setBatchRowIndex(0);
        job.setRequestedCount(10);
        job.setStatus(status);
        return job;
    }

    @Test
    void theCurrentChildIsTheLeasedOneElseTheQueuedOne() {
        QuestionGenerationBatch batch = batchOf(staffA);
        QuestionGenerationJob done = child(0, JobStatus.COMPLETED);
        QuestionGenerationJob running = child(1, JobStatus.RUNNING);
        when(jobs.findByBatchIdOrderByBatchSeqAsc(40L)).thenReturn(List.of(done, running));
        when(items.countStagesByBatch(40L)).thenReturn(List.of(new Object[] {ItemStage.SAVED, 9L},
                new Object[] {ItemStage.FAILED, 1L}, new Object[] {ItemStage.GENERATING, 1L}));

        GenerationBatchResponse response = service.get(batch.getPublicId(), staffA);

        assertThat(response.currentJobPublicId()).isEqualTo(running.getPublicId());
        assertThat(response.succeededCount()).isEqualTo(9);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.openCount()).isEqualTo(10);
        assertThat(response.children()).hasSize(2);

        when(jobs.findByBatchIdOrderByBatchSeqAsc(40L)).thenReturn(List.of(done, child(1, JobStatus.QUEUED)));
        assertThat(service.get(batch.getPublicId(), staffA).currentJobPublicId()).isNotNull();
        when(jobs.findByBatchIdOrderByBatchSeqAsc(40L)).thenReturn(List.of(done));
        assertThat(service.get(batch.getPublicId(), staffA).currentJobPublicId()).isNull();
    }

    @Test
    void staffCannotReachAnotherWritersBatchButAManagerCan() {
        QuestionGenerationBatch batch = batchOf(staffA);

        assertThatThrownBy(() -> service.require(batch.getPublicId(), staffB))
                .isInstanceOf(GenerationBatchNotFoundException.class);
        assertThat(service.require(batch.getPublicId(), manager)).isSameAs(batch);
    }

    @Test
    void staffAlwaysListOnlyTheirOwnBatchesEvenWithMineFalse() {
        Page<QuestionGenerationBatch> empty = new PageImpl<>(List.of());
        when(batches.findByRequestedByUserPublicIdAndDeletedFalse(any(), any(Pageable.class))).thenReturn(empty);
        when(batches.findByDeletedFalse(any(Pageable.class))).thenReturn(empty);

        service.list(0, 20, false, staffA);
        verify(batches).findByRequestedByUserPublicIdAndDeletedFalse(eq(staffA.userId()), any(Pageable.class));
        verify(batches, never()).findByDeletedFalse(any(Pageable.class));

        service.list(0, 20, false, manager);
        verify(batches).findByDeletedFalse(any(Pageable.class));
        service.list(0, 20, true, manager);
        verify(batches).findByRequestedByUserPublicIdAndDeletedFalse(eq(manager.userId()), any(Pageable.class));
    }
}
