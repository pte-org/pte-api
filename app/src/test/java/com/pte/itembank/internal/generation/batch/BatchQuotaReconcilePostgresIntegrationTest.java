package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Quota refunds of cancelled-before-start children, fatal stops, and the reconciler's repair of a missed hook. */
@EnabledIfSystemProperty(named = BatchPostgresDatabase.URL_PROPERTY, matches = ".+")
class BatchQuotaReconcilePostgresIntegrationTest extends BatchPostgresIntegrationSupport {

    private static final int PER_CHILD = 2;

    @Test
    void childrenCancelledBeforeTheyStartedAreRefundedButARunThatFailedStillCounts() {
        UUID author = UUID.randomUUID();
        QuestionGenerationBatch batch = batchWithChildren(author, 3, PER_CHILD);
        JobClaim claim = leases.claimNext("worker-a").orElseThrow();
        failAll(claim);
        leases.finish(claim, null, null);
        assertThat(sumForAuthor(author)).as("all three children before the cancel").isEqualTo(6);

        batchService.cancel(batch.getPublicId(), new CurrentUser(author, null, List.of(SecurityRoles.PLATFORM_ADMIN)));

        assertThat(sumForAuthor(author)).isEqualTo(PER_CHILD);
        assertThat(tx(() -> jobs.sumRequestedSince(Instant.now().minusSeconds(3_600)))).isEqualTo(PER_CHILD);
        assertThat(batchStatus(batch.getId())).isEqualTo(BatchStatus.CANCELLED);
    }

    @ParameterizedTest
    @CsvSource({
            QuestionGenerationConstants.GENERATION_REQUESTER_NOT_ALLOWED + ", CANCELLED",
            QuestionGenerationConstants.GENERATION_PROVIDER_AUTH + ", FAILED",
            QuestionGenerationConstants.GENERATION_PROVIDER_UNAVAILABLE + ", FAILED"})
    void aFatalFailureStopsAndRefundsTheRemainingChildren(String code, JobStatus childStatus) {
        UUID author = UUID.randomUUID();
        QuestionGenerationBatch batch = batchWithChildren(author, 3, PER_CHILD);
        JobClaim claim = leases.claimNext("worker-a").orElseThrow();
        if (childStatus == JobStatus.CANCELLED) {
            txRun(() -> items.cancelOpen(claim.jobId(), claim.workerId(), Instant.now()));
        } else {
            txRun(() -> items.failOpen(claim.jobId(), code, claim.workerId(), Instant.now()));
        }

        leases.finish(claim, childStatus, code);

        List<QuestionGenerationJob> children = children(batch.getId());
        assertThat(children.get(0).getStatus()).isEqualTo(childStatus);
        assertThat(children.subList(1, 3)).allSatisfy(child -> {
            assertThat(child.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(child.getStartedAt()).isNull();
            assertThat(child.getFailureCode()).isEqualTo(code);
        });
        QuestionGenerationBatch closed = tx(() -> batches.findById(batch.getId()).orElseThrow());
        assertThat(closed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(closed.getFailureCode()).isEqualTo(code);
        assertThat(sumForAuthor(author)).as("only the child that ran counts").isEqualTo(PER_CHILD);
    }

    @Test
    void theReconcilerClosesAnOpenBatchWhoseChildrenAllEnded() {
        UUID author = UUID.randomUUID();
        QuestionGenerationBatch batch = batchWithChildren(author, 1, PER_CHILD);
        JobClaim claim = leases.claimNext("worker-a").orElseThrow();
        saveAll(claim);
        // A missed hook: the child is closed directly, without chaining, so the batch stays RUNNING.
        txRun(() -> jobs.finish(claim.jobId(), claim.workerId(), JobStatus.COMPLETED, null, Instant.now()));
        assertThat(batchStatus(batch.getId())).isEqualTo(BatchStatus.RUNNING);

        new BatchReconciler(batches, chaining).tick();

        QuestionGenerationBatch closed = tx(() -> batches.findById(batch.getId()).orElseThrow());
        assertThat(closed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(closed.getSucceededCount()).isEqualTo(PER_CHILD);
        assertThat(closed.getFinishedAt()).isNotNull();
    }

    @Test
    void theReconcilerClosesACancelledBatchAsCancelledAndQueuesALostPromotionOtherwise() {
        QuestionGenerationBatch cancelled = batchWithChildren(UUID.randomUUID(), 2, PER_CHILD);
        QuestionGenerationBatch stalled = batchWithChildren(UUID.randomUUID(), 2, PER_CHILD);
        Long first = children(cancelled.getId()).get(0).getId();
        Long stalledFirst = children(stalled.getId()).get(0).getId();
        txRun(() -> {
            batches.requestCancel(cancelled.getId(), Instant.now());
            jobs.cancelPending(cancelled.getId(), Instant.now(), null);
        });
        // A lost promotion: the first child of the second batch ended, but nobody queued the next one.
        txRun(() -> jobs.cancelQueued(job(stalledFirst).getPublicId(), Instant.now()));

        new BatchReconciler(batches, chaining).tick();

        assertThat(batchStatus(cancelled.getId())).isEqualTo(BatchStatus.CANCELLED);
        assertThat(job(first).getStatus()).isEqualTo(JobStatus.CANCELLED);
        assertThat(children(stalled.getId()).get(1).getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(batchStatus(stalled.getId())).isEqualTo(BatchStatus.QUEUED);
    }

    private void saveAll(JobClaim claim) {
        for (int index = 0; index < PER_CHILD; index++) {
            int item = index;
            txRun(() -> items.markSaved(claim.jobId(), item, UUID.randomUUID(), claim.workerId(), Instant.now()));
        }
    }

    private void failAll(JobClaim claim) {
        txRun(() -> items.failOpen(claim.jobId(), QuestionGenerationConstants.GENERATION_INTERNAL_ERROR,
                claim.workerId(), Instant.now()));
    }
}
