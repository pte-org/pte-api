package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancel racing a child finishing and a claim of the next child, each order forced with latches: the first
 * transaction takes the batch row lock and waits until Postgres shows the second one blocked on a lock, then
 * finishes. Both must complete (no deadlock) and leave the batch closed with no waiting child left behind.
 */
@EnabledIfSystemProperty(named = BatchPostgresDatabase.URL_PROPERTY, matches = ".+")
class BatchCancelRacePostgresIntegrationTest extends BatchPostgresIntegrationSupport {

    private static final long STEP_SECONDS = 20;

    private UUID author;
    private CurrentUser caller;

    private QuestionGenerationBatch runningBatch() {
        author = UUID.randomUUID();
        caller = new CurrentUser(author, null, List.of(SecurityRoles.PLATFORM_ADMIN));
        return batchWithChildren(author, 3, 1);
    }

    @Test
    void cancelHoldingTheBatchLockThenTheRunningChildFinishes() throws Exception {
        QuestionGenerationBatch batch = runningBatch();
        JobClaim claim = leases.claimNext("worker-a").orElseThrow();

        race(() -> batchService.cancel(batch.getPublicId(), caller), () -> leases.finish(claim, null, null),
                batch.getId());

        assertBatchCancelledWithNothingWaiting(batch.getId());
        assertThat(job(claim.jobId()).getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void theRunningChildFinishesHoldingTheBatchLockThenCancel() throws Exception {
        QuestionGenerationBatch batch = runningBatch();
        JobClaim claim = leases.claimNext("worker-a").orElseThrow();

        race(() -> leases.finish(claim, null, null), () -> batchService.cancel(batch.getPublicId(), caller),
                batch.getId());

        // The finish promoted seq 1 to QUEUED; the cancel that followed must have stopped it.
        assertBatchCancelledWithNothingWaiting(batch.getId());
        assertThat(job(claim.jobId()).getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void cancelHoldingTheBatchLockThenTheNextChildIsClaimed() throws Exception {
        QuestionGenerationBatch batch = runningBatch();
        leases.finish(leases.claimNext("worker-a").orElseThrow(), null, null);
        CompletableFuture<Optional<JobClaim>> claimed = new CompletableFuture<>();

        race(() -> batchService.cancel(batch.getPublicId(), caller),
                () -> claimed.complete(leases.claimNext("worker-b")), batch.getId());

        assertThat(claimed.get()).isEmpty();
        assertBatchCancelledWithNothingWaiting(batch.getId());
        assertThat(children(batch.getId()).get(1).getStartedAt()).isNull();
    }

    @Test
    void theNextChildIsClaimedHoldingTheBatchLockThenCancel() throws Exception {
        QuestionGenerationBatch batch = runningBatch();
        leases.finish(leases.claimNext("worker-a").orElseThrow(), null, null);
        CompletableFuture<Optional<JobClaim>> claimed = new CompletableFuture<>();

        race(() -> claimed.complete(leases.claimNext("worker-b")),
                () -> batchService.cancel(batch.getPublicId(), caller), batch.getId());

        JobClaim running = claimed.get().orElseThrow();
        assertThat(job(running.jobId()).getStatus()).isEqualTo(JobStatus.CANCELLING);
        assertThat(batchStatus(batch.getId())).isEqualTo(BatchStatus.CANCELLING);
        // The worker honours the cancel request; its finish hook closes the batch.
        leases.finish(running, JobStatus.CANCELLED, null);
        assertBatchCancelledWithNothingWaiting(batch.getId());
    }

    /**
     * Runs {@code first} inside a transaction that takes the batch row lock before it and keeps it until
     * {@code second} (its own transaction, on another thread) is seen waiting on a lock.
     */
    private void race(Runnable first, Runnable second, Long batchId) throws Exception {
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch secondIsBlocked = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> txRun(() -> {
            batches.findByIdForUpdate(batchId);
            firstHoldsLock.countDown();
            await(secondIsBlocked);
            first.run();
        }));
        await(firstHoldsLock);
        int waitingBefore = lockWaiters();
        CompletableFuture<Void> contender = CompletableFuture.runAsync(second);
        waitUntil(() -> lockWaiters() > waitingBefore || contender.isDone());
        assertThat(contender).as("second transaction must block on the batch row lock").isNotDone();
        secondIsBlocked.countDown();

        holder.get(STEP_SECONDS, TimeUnit.SECONDS);
        contender.get(STEP_SECONDS, TimeUnit.SECONDS);
    }

    private void assertBatchCancelledWithNothingWaiting(Long batchId) {
        assertThat(batchStatus(batchId)).isEqualTo(BatchStatus.CANCELLED);
        List<QuestionGenerationJob> children = children(batchId);
        assertThat(children).extracting(QuestionGenerationJob::getStatus)
                .allMatch(JobStatus.TERMINAL::contains)
                .doesNotContain(JobStatus.PENDING, JobStatus.QUEUED);
        assertThat(children.get(2).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(tx(() -> batches.findById(batchId).orElseThrow().getFinishedAt())).isBefore(Instant.now());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(STEP_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch not released in time");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void waitUntil(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(STEP_SECONDS);
        while (!condition.get()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Condition not reached in time");
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }
}
