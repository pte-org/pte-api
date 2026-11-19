package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** The claim under real concurrency: the cap holds across claimers, and the claim order is the designed one. */
@EnabledIfSystemProperty(named = BatchPostgresDatabase.URL_PROPERTY, matches = ".+")
class BatchClaimPostgresIntegrationTest extends BatchPostgresIntegrationSupport {

    private static final int CLAIMERS = 6;
    private static final int ROUNDS = 3;

    @Test
    void concurrentClaimersNeverHoldMoreLiveLeasesThanTheCap() throws Exception {
        when(limits.maxConcurrentJobs()).thenReturn(2);
        for (int job = 0; job < 8; job++) {
            standalone(UUID.randomUUID(), true);
        }
        CyclicBarrier start = new CyclicBarrier(CLAIMERS);
        List<Callable<Integer>> claimers = new ArrayList<>();
        for (int claimer = 0; claimer < CLAIMERS; claimer++) {
            String workerId = "worker-" + claimer;
            claimers.add(() -> {
                int taken = 0;
                for (int round = 0; round < ROUNDS; round++) {
                    start.await(30, TimeUnit.SECONDS);
                    taken += leases.claimNext(workerId).isPresent() ? 1 : 0;
                    assertThat(tx(() -> jobs.countLiveLeases(Instant.now()))).isLessThanOrEqualTo(2);
                }
                return taken;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(CLAIMERS);
        int claimed = 0;
        try {
            for (Future<Integer> result : pool.invokeAll(claimers, 60, TimeUnit.SECONDS)) {
                claimed += result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(claimed).isEqualTo(2);
        assertThat(tx(() -> jobs.countLiveLeases(Instant.now()))).isEqualTo(2);
    }

    @Test
    void standaloneAndPreviewJobsAreClaimedBeforeBatchChildrenCreatedEarlier() {
        QuestionGenerationBatch batch = batchWithChildren(UUID.randomUUID(), 2, 1);
        UUID author = UUID.randomUUID();
        QuestionGenerationJob preview = standalone(author, false);
        QuestionGenerationJob saving = standalone(author, true);

        List<Long> order = new ArrayList<>();
        for (int claim = 0; claim < 3; claim++) {
            leases.claimNext("worker-" + claim).map(JobClaim::jobId).ifPresent(order::add);
        }

        Long firstChild = children(batch.getId()).get(0).getId();
        assertThat(order).containsExactly(preview.getId(), saving.getId(), firstChild);
        assertThat(children(batch.getId()).get(1).getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(batchStatus(batch.getId())).isEqualTo(com.pte.itembank.internal.generation.domain.BatchStatus.RUNNING);
    }

    @Test
    void anExpiredLeaseIsResumedEvenWhenTheCapIsReached() {
        when(limits.maxConcurrentJobs()).thenReturn(1);
        QuestionGenerationJob live = standalone(UUID.randomUUID(), true);
        QuestionGenerationJob dead = standalone(UUID.randomUUID(), true);
        QuestionGenerationJob waiting = standalone(UUID.randomUUID(), true);
        Instant now = Instant.now();
        tx(() -> jobs.markRunning(live.getId(), "worker-live", now.plus(Duration.ofMinutes(10)), now));
        tx(() -> jobs.markRunning(dead.getId(), "worker-dead", now.minus(Duration.ofMinutes(1)), now));

        Optional<JobClaim> resumed = leases.claimNext("worker-new");
        Optional<JobClaim> blocked = leases.claimNext("worker-other");

        assertThat(resumed).map(JobClaim::jobId).contains(dead.getId());
        assertThat(job(dead.getId()).getWorkerId()).isEqualTo("worker-new");
        assertThat(blocked).isEmpty();
        assertThat(job(waiting.getId()).getStatus()).isEqualTo(JobStatus.QUEUED);
    }
}
