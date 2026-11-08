package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls for a job and runs it on a dedicated thread, so a job that takes minutes never blocks the shared
 * scheduler thread. At most one job runs per instance; another instance (or this one after a restart) can resume a
 * job once its lease expired.
 */
@Component
@ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
public class QuestionGenerationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionGenerationWorker.class);

    private final String workerId = QuestionGenerationConstants.WORKER_ID_PREFIX + UUID.randomUUID();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final JobLeaseService leases;
    private final GenerationJobRunner runner;
    private final ThreadPoolTaskExecutor executor;
    private final GenerationLiveFeed liveFeed;

    public QuestionGenerationWorker(JobLeaseService leases, GenerationJobRunner runner,
                                    @Qualifier(QuestionGenerationConstants.EXECUTOR_NAME)
                                    ThreadPoolTaskExecutor executor, GenerationLiveFeed liveFeed) {
        this.leases = leases;
        this.runner = runner;
        this.executor = executor;
        this.liveFeed = liveFeed;
    }

    @Scheduled(fixedDelayString = "${itembank.generation.worker.poll-delay-ms:3000}")
    public void tick() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        Optional<JobClaim> claim;
        try {
            claim = leases.claimNext(workerId);
        } catch (RuntimeException unavailable) {
            busy.set(false);
            LOG.warn("Generation claim deferred: errorType={}", unavailable.getClass().getSimpleName());
            return;
        }
        if (claim.isEmpty()) {
            busy.set(false);
            return;
        }
        try {
            executor.execute(() -> runClaim(claim.get()));
        } catch (RejectedExecutionException rejected) {
            busy.set(false);
        }
    }

    private void runClaim(JobClaim claim) {
        try {
            runner.run(claim);
        } catch (RuntimeException aborted) {
            // The lease lapses and the job is resumed later; the cause is a class name only, never a message.
            LOG.warn("Generation job aborted: jobId={}, errorType={}", claim.jobPublicId(),
                    aborted.getClass().getSimpleName());
        } finally {
            // Also after an abort: the watchers reconnect and find the job resumed or ended.
            liveFeed.jobEnded(claim.jobPublicId());
            busy.set(false);
        }
    }
}
