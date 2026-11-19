package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the batch statements on H2 (JPQL validity and guards). {@code rows} is a keyword in H2, so identifiers that are
 * keywords are quoted here; Postgres needs no quoting. Lock and partial-index behaviour is Postgres-only.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.auto_quote_keyword=true")
class GenerationBatchQueriesTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired private GenerationBatchRepository batches;
    @Autowired private GenerationJobRepository jobs;
    @Autowired private GenerationJobItemRepository items;
    @Autowired private EntityManager entityManager;

    private QuestionGenerationBatch batch(int total) {
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setRequestedByUserPublicId(UUID.randomUUID());
        batch.setPool(QuestionPool.PRACTICE);
        batch.setRows(List.of(new BatchRow("MC_READING_SINGLE", CefrLevel.B2, total, List.of(), ImageKind.AUTO)));
        batch.setTotalRequested(total);
        return batches.saveAndFlush(batch);
    }

    private QuestionGenerationJob child(QuestionGenerationBatch batch, int seq, JobStatus status, int count) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(batch.getRequestedByUserPublicId());
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setRequestedCount(count);
        job.setStatus(status);
        job.setBatchId(batch.getId());
        job.setBatchSeq(seq);
        job.setBatchRowIndex(0);
        QuestionGenerationJob saved = jobs.saveAndFlush(job);
        for (int index = 0; index < count; index++) {
            QuestionGenerationJobItem item = new QuestionGenerationJobItem();
            item.setJobId(saved.getId());
            item.setItemIndex(index);
            item.setUpdatedAt(NOW);
            items.save(item);
        }
        items.flush();
        return saved;
    }

    private JobStatus statusOf(Long jobId) {
        entityManager.clear();
        return jobs.findById(jobId).orElseThrow().getStatus();
    }

    private QuestionGenerationBatch reload(Long batchId) {
        entityManager.clear();
        return batches.findById(batchId).orElseThrow();
    }

    @Test
    void theRowsRoundTripAsJson() {
        QuestionGenerationBatch saved = batch(3);

        assertThat(reload(saved.getId()).getRows()).singleElement()
                .satisfies(row -> assertThat(row.cefrLevel()).isEqualTo(CefrLevel.B2));
    }

    @Test
    void promoteNextQueuesOnlyTheLowestWaitingChild() {
        QuestionGenerationBatch batch = batch(3);
        QuestionGenerationJob first = child(batch, 0, JobStatus.COMPLETED, 1);
        QuestionGenerationJob second = child(batch, 1, JobStatus.PENDING, 1);
        QuestionGenerationJob third = child(batch, 2, JobStatus.PENDING, 1);

        assertThat(jobs.promoteNext(batch.getId())).isEqualTo(1);
        assertThat(statusOf(second.getId())).isEqualTo(JobStatus.QUEUED);
        assertThat(statusOf(third.getId())).isEqualTo(JobStatus.PENDING);
        assertThat(statusOf(first.getId())).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void stoppingABatchCancelsWaitingChildrenAndTheirItemsButNotTheRunningOne() {
        QuestionGenerationBatch batch = batch(5);
        QuestionGenerationJob running = child(batch, 0, JobStatus.QUEUED, 2);
        jobs.markRunning(running.getId(), "worker-a", NOW.plus(Duration.ofMinutes(5)), NOW);
        QuestionGenerationJob waiting = child(batch, 1, JobStatus.PENDING, 3);

        assertThat(items.cancelOpenUnleasedByBatch(batch.getId(), NOW)).isEqualTo(3);
        assertThat(jobs.cancelPending(batch.getId(), NOW, "GENERATION_PROVIDER_AUTH")).isEqualTo(1);

        entityManager.clear();
        QuestionGenerationJob cancelled = jobs.findById(waiting.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(cancelled.getStartedAt()).isNull();
        assertThat(cancelled.getFailureCode()).isEqualTo("GENERATION_PROVIDER_AUTH");
        assertThat(statusOf(running.getId())).isEqualTo(JobStatus.RUNNING);
        assertThat(jobs.existsByBatchIdAndStatusIn(batch.getId(), JobStatus.OPEN)).isTrue();
    }

    @Test
    void countersComeFromTheItemsOfEveryChild() {
        QuestionGenerationBatch batch = batch(4);
        QuestionGenerationJob first = child(batch, 0, JobStatus.QUEUED, 2);
        jobs.markRunning(first.getId(), "worker-a", NOW.plus(Duration.ofMinutes(5)), NOW);
        items.markSaved(first.getId(), 0, UUID.randomUUID(), "worker-a", NOW);
        items.fail(first.getId(), 1, "X", null, "worker-a", NOW);
        child(batch, 1, JobStatus.PENDING, 2);
        items.cancelOpenUnleasedByBatch(batch.getId(), NOW);

        batches.refreshCounters(batch.getId(), NOW);

        QuestionGenerationBatch counted = reload(batch.getId());
        assertThat(counted.getSucceededCount()).isEqualTo(1);
        assertThat(counted.getFailedCount()).isEqualTo(1);
        assertThat(counted.getCancelledCount()).isEqualTo(2);
        assertThat(items.countStagesByBatch(batch.getId())).extracting(row -> row[0])
                .contains(ItemStage.SAVED, ItemStage.FAILED, ItemStage.CANCELLED);
    }

    @Test
    void cancelAndCloseAreGuardedByTheBatchStatus() {
        QuestionGenerationBatch batch = batch(1);

        assertThat(batches.markRunning(batch.getId(), NOW)).isEqualTo(1);
        assertThat(batches.markRunning(batch.getId(), NOW)).isZero();
        assertThat(batches.requestCancel(batch.getId(), NOW)).isEqualTo(1);
        assertThat(reload(batch.getId()).getStatus()).isEqualTo(BatchStatus.CANCELLING);
        assertThat(batches.requestCancel(batch.getId(), NOW)).isZero();
        assertThat(batches.close(batch.getId(), BatchStatus.CANCELLED, null, NOW)).isEqualTo(1);
        assertThat(batches.close(batch.getId(), BatchStatus.COMPLETED, null, NOW)).isZero();
        QuestionGenerationBatch closed = reload(batch.getId());
        assertThat(closed.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        assertThat(closed.isCancelRequested()).isTrue();
        assertThat(closed.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    void theReconcilerFindsOpenBatchesWithoutOpenOrWithOnlyWaitingChildren() {
        QuestionGenerationBatch orphan = batch(1);
        child(orphan, 0, JobStatus.COMPLETED, 1);
        QuestionGenerationBatch stuck = batch(1);
        child(stuck, 0, JobStatus.COMPLETED, 1);
        child(stuck, 1, JobStatus.PENDING, 1);
        QuestionGenerationBatch healthy = batch(1);
        child(healthy, 0, JobStatus.QUEUED, 1);

        assertThat(batches.findOpenIdsWithoutOpenChild()).contains(orphan.getId())
                .doesNotContain(stuck.getId(), healthy.getId());
        assertThat(batches.findOpenIdsWithOnlyPendingChildren()).contains(stuck.getId())
                .doesNotContain(orphan.getId(), healthy.getId());
    }
}
