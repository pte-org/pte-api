package com.pte.itembank.internal.generation.repository;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
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
 * Runs every JPQL statement of the two repositories on H2: it proves the queries are valid and that the guards
 * (lease owner, open stages, expected status) behave. The two native Postgres statements (skip-locked claim and
 * advisory lock) and the partial unique indexes are covered by the opt-in Postgres test.
 */
@DataJpaTest
class GenerationRepositoryQueriesTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant LEASE = NOW.plus(Duration.ofMinutes(10));

    @Autowired private GenerationJobRepository jobs;
    @Autowired private GenerationJobItemRepository items;
    @Autowired private EntityManager entityManager;

    private QuestionGenerationJob running(String worker) {
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(UUID.randomUUID());
        job.setTaskTypeKey("MC_READING_SINGLE");
        job.setCefrLevel(CefrLevel.B2);
        job.setPool(QuestionPool.PRACTICE);
        job.setRequestedCount(3);
        QuestionGenerationJob saved = jobs.saveAndFlush(job);
        jobs.markRunning(saved.getId(), worker, LEASE, NOW);
        return saved;
    }

    private void addItems(Long jobId, int count) {
        for (int index = 0; index < count; index++) {
            QuestionGenerationJobItem item = new QuestionGenerationJobItem();
            item.setJobId(jobId);
            item.setItemIndex(index);
            item.setUpdatedAt(NOW);
            items.save(item);
        }
        items.flush();
    }

    private QuestionGenerationJob reload(Long id) {
        entityManager.clear();
        return jobs.findById(id).orElseThrow();
    }

    private ItemStage stageOf(Long jobId, int index) {
        entityManager.clear();
        return items.findByJobIdOrderByItemIndexAsc(jobId).get(index).getStage();
    }

    @Test
    void markRunningOwnsTheJobAndKeepsTheFirstStartTime() {
        QuestionGenerationJob job = running("worker-a");
        QuestionGenerationJob owned = reload(job.getId());

        assertThat(owned.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(owned.getWorkerId()).isEqualTo("worker-a");
        assertThat(owned.getLeaseUntil()).isEqualTo(LEASE);
        assertThat(owned.getStartedAt()).isEqualTo(NOW);

        jobs.markRunning(job.getId(), "worker-b", LEASE.plusSeconds(60), NOW.plusSeconds(60));
        assertThat(reload(job.getId()).getStartedAt()).isEqualTo(NOW);
        assertThat(reload(job.getId()).getWorkerId()).isEqualTo("worker-b");
    }

    @Test
    void onlyTheOwnerCanRenewTheLease() {
        QuestionGenerationJob job = running("worker-a");

        assertThat(jobs.renewLease(job.getId(), "worker-b", LEASE.plusSeconds(1))).isZero();
        assertThat(jobs.renewLease(job.getId(), "worker-a", LEASE.plusSeconds(1))).isEqualTo(1);
        assertThat(reload(job.getId()).getLeaseUntil()).isEqualTo(LEASE.plusSeconds(1));
    }

    @Test
    void statusIfLeaseHeldIsEmptyForAnotherWorkerAndSeesACancelRequest() {
        QuestionGenerationJob job = running("worker-a");
        assertThat(jobs.statusIfLeaseHeld(job.getId(), "worker-a")).containsExactly(JobStatus.RUNNING);
        assertThat(jobs.statusIfLeaseHeld(job.getId(), "worker-b")).isEmpty();

        assertThat(jobs.markCancelling(job.getPublicId())).isEqualTo(1);
        assertThat(jobs.statusIfLeaseHeld(job.getId(), "worker-a")).containsExactly(JobStatus.CANCELLING);
    }

    @Test
    void cancelQueuedOnlyAffectsAQueuedJobAndMarkCancellingOnlyARunningOne() {
        QuestionGenerationJob queued = new QuestionGenerationJob();
        queued.setRequestedByUserPublicId(UUID.randomUUID());
        queued.setTaskTypeKey("MC_READING_SINGLE");
        queued.setCefrLevel(CefrLevel.B2);
        queued.setPool(QuestionPool.PRACTICE);
        queued.setRequestedCount(1);
        QuestionGenerationJob job = jobs.saveAndFlush(queued);

        assertThat(jobs.markCancelling(job.getPublicId())).isZero();
        assertThat(jobs.cancelQueued(job.getPublicId(), NOW)).isEqualTo(1);
        assertThat(jobs.cancelQueued(job.getPublicId(), NOW)).isZero();
        assertThat(reload(job.getId()).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(reload(job.getId()).getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    void finishNeedsTheOwnerAndARunningOrCancellingJob() {
        QuestionGenerationJob job = running("worker-a");

        assertThat(jobs.finish(job.getId(), "worker-b", JobStatus.COMPLETED, null, NOW)).isZero();
        assertThat(jobs.finish(job.getId(), "worker-a", JobStatus.FAILED, "GENERATION_PROVIDER_AUTH", NOW))
                .isEqualTo(1);
        QuestionGenerationJob finished = reload(job.getId());
        assertThat(finished.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finished.getFailureCode()).isEqualTo("GENERATION_PROVIDER_AUTH");
        assertThat(jobs.finish(job.getId(), "worker-a", JobStatus.COMPLETED, null, NOW)).isZero();
    }

    @Test
    void aCancelRequestWhoseWorkerDiedIsClosedAndTheWorkerReleased() {
        QuestionGenerationJob job = running("worker-a");
        jobs.markCancelling(job.getPublicId());

        assertThat(jobs.finishStaleCancelling(job.getId(), NOW)).isEqualTo(1);

        QuestionGenerationJob closed = reload(job.getId());
        assertThat(closed.getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(closed.getWorkerId()).isNull();
    }

    @Test
    void itemWritesAreGuardedByTheLeaseOwner() {
        QuestionGenerationJob job = running("worker-a");
        addItems(job.getId(), 2);

        assertThat(items.advance(job.getId(), 0, ItemStage.GENERATING, "worker-b", NOW)).isZero();
        assertThat(items.advance(job.getId(), 0, ItemStage.GENERATING, "worker-a", NOW)).isEqualTo(1);
        assertThat(stageOf(job.getId(), 0)).isEqualTo(ItemStage.GENERATING);
        assertThat(items.fail(job.getId(), 0, "X", "note", "worker-b", NOW)).isZero();
        assertThat(items.markSaved(job.getId(), 0, UUID.randomUUID(), "worker-b", NOW)).isZero();
        assertThat(items.cancelOpen(job.getId(), "worker-b", NOW)).isZero();
        assertThat(items.failOpen(job.getId(), "X", "worker-b", NOW)).isZero();
    }

    @Test
    void aFinishedItemCannotBeMovedBackOrFailedAgain() {
        QuestionGenerationJob job = running("worker-a");
        addItems(job.getId(), 1);
        UUID questionId = UUID.randomUUID();

        assertThat(items.markSaved(job.getId(), 0, questionId, "worker-a", NOW)).isEqualTo(1);
        assertThat(items.markSaved(job.getId(), 0, questionId, "worker-a", NOW)).isZero();
        assertThat(items.advance(job.getId(), 0, ItemStage.GENERATING, "worker-a", NOW)).isZero();
        assertThat(items.fail(job.getId(), 0, "X", null, "worker-a", NOW)).isZero();
        entityManager.clear();
        QuestionGenerationJobItem saved = items.findByJobIdOrderByItemIndexAsc(job.getId()).get(0);
        assertThat(saved.getStage()).isEqualTo(ItemStage.SAVED);
        assertThat(saved.getQuestionPublicId()).isEqualTo(questionId);
    }

    @Test
    void cancelOpenAndFailOpenOnlyTouchTheUnfinishedItems() {
        QuestionGenerationJob job = running("worker-a");
        addItems(job.getId(), 3);
        items.markSaved(job.getId(), 0, UUID.randomUUID(), "worker-a", NOW);
        items.advance(job.getId(), 1, ItemStage.REVIEWING, "worker-a", NOW);

        assertThat(items.failOpen(job.getId(), "GENERATION_PROVIDER_UNAVAILABLE", "worker-a", NOW)).isEqualTo(2);
        assertThat(stageOf(job.getId(), 0)).isEqualTo(ItemStage.SAVED);
        assertThat(stageOf(job.getId(), 1)).isEqualTo(ItemStage.FAILED);
        assertThat(stageOf(job.getId(), 2)).isEqualTo(ItemStage.FAILED);
        assertThat(items.cancelOpen(job.getId(), "worker-a", NOW)).isZero();
    }

    @Test
    void anUnleasedCancelClosesOpenItemsOfAQueuedJob() {
        QuestionGenerationJob queued = new QuestionGenerationJob();
        queued.setRequestedByUserPublicId(UUID.randomUUID());
        queued.setTaskTypeKey("MC_READING_SINGLE");
        queued.setCefrLevel(CefrLevel.B2);
        queued.setPool(QuestionPool.PRACTICE);
        queued.setRequestedCount(2);
        QuestionGenerationJob job = jobs.saveAndFlush(queued);
        addItems(job.getId(), 2);

        assertThat(items.cancelOpenUnleased(job.getId(), NOW)).isEqualTo(2);
        assertThat(stageOf(job.getId(), 0)).isEqualTo(ItemStage.CANCELLED);
    }

    @Test
    void countersAreDerivedFromTheItemStages() {
        QuestionGenerationJob job = running("worker-a");
        addItems(job.getId(), 3);
        items.markSaved(job.getId(), 0, UUID.randomUUID(), "worker-a", NOW);
        items.markSaved(job.getId(), 1, UUID.randomUUID(), "worker-a", NOW);
        items.fail(job.getId(), 2, "GENERATION_INTERNAL_ERROR", null, "worker-a", NOW);

        jobs.refreshCounters(job.getId());

        QuestionGenerationJob refreshed = reload(job.getId());
        assertThat(refreshed.getSucceededCount()).isEqualTo(2);
        assertThat(refreshed.getFailedCount()).isEqualTo(1);
    }

    @Test
    void theQuotaSumsAndActiveCountsUseTheRightWindowAndStates() {
        QuestionGenerationJob job = running("worker-a");
        UUID requester = job.getRequestedByUserPublicId();

        assertThat(jobs.sumRequestedSinceByUser(requester, Instant.EPOCH)).isEqualTo(3);
        assertThat(jobs.sumRequestedSinceByUser(requester, Instant.now().plus(Duration.ofDays(1)))).isZero();
        assertThat(jobs.sumRequestedSince(Instant.EPOCH)).isGreaterThanOrEqualTo(3);
        assertThat(jobs.countByStatusIn(JobStatus.ACTIVE)).isGreaterThanOrEqualTo(1);
        assertThat(jobs.existsByRequestedByUserPublicIdAndStatusIn(requester, JobStatus.ACTIVE)).isTrue();
        assertThat(jobs.existsByRequestedByUserPublicIdAndStatusIn(requester, List.of(JobStatus.COMPLETED))).isFalse();
    }
}
