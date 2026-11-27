package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.itembank.internal.generation.stop.GenerationStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls for jobs and runs each on its own thread of the generation pool, so a job that takes minutes never blocks
 * the shared scheduler thread. Every tick claims jobs until the pool is full or nothing is claimable; the claim
 * itself enforces the parallel-run setting across instances. Another instance (or this one after a restart) can
 * resume a job once its lease expired.
 */
@Component
@ConditionalOnProperty(prefix = "itembank.generation", name = "enabled", havingValue = "true")
public class QuestionGenerationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionGenerationWorker.class);

    private final String workerId = QuestionGenerationConstants.WORKER_ID_PREFIX + UUID.randomUUID();
    /** Jobs this instance runs right now; never more than the pool has threads. */
    private final AtomicInteger running = new AtomicInteger();
    /** Every job this instance runs, so a cancel or a pause can stop it. */
    private final Map<UUID, RunningJob> runningJobs = new ConcurrentHashMap<>();
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
        while (running.get() < executor.getMaxPoolSize()) {
            Optional<JobClaim> claim;
            try {
                claim = leases.claimNext(workerId);
            } catch (RuntimeException unavailable) {
                LOG.warn("Generation claim deferred: errorType={}", unavailable.getClass().getSimpleName());
                return;
            }
            if (claim.isEmpty()) {
                return;
            }
            JobClaim claimed = claim.get();
            // Registered before it starts, so a cancel arriving at once finds it and a fast job cannot end first.
            GenerationStop stop = new GenerationStop();
            FutureTask<Void> task = new FutureTask<>(() -> runClaim(claimed, stop), null);
            runningJobs.put(claimed.jobPublicId(), new RunningJob(task, stop));
            running.incrementAndGet();
            try {
                executor.execute(task);
            } catch (RejectedExecutionException rejected) {
                // The claimed job keeps its lease until it expires and is then resumed, as after a crash.
                runningJobs.remove(claimed.jobPublicId());
                running.decrementAndGet();
                return;
            }
        }
    }

    /**
     * Interrupts the job a person cancelled, after the CANCELLING status is committed: the provider call in progress
     * fails at once and the runner closes the job as cancelled. A job not running here is left to its own instance.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelRequested(GenerationCancelRequested request) {
        stop(request.jobPublicId());
    }

    /** Interrupts a job whose batch was paused, after PAUSING is committed; the runner hands it back to waiting. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPauseRequested(GenerationPauseRequested request) {
        stop(request.jobPublicId());
    }

    /**
     * The signal first, so no retry, fallback or save goes on and the response being read is closed; then the
     * interrupt, for a thread that sleeps or waits.
     */
    private void stop(UUID jobPublicId) {
        RunningJob job = runningJobs.get(jobPublicId);
        if (job != null) {
            job.stop().request();
            job.task().cancel(true);
        }
    }

    private void runClaim(JobClaim claim, GenerationStop stop) {
        stop.bind();
        try {
            liveFeed.jobStarted(claim.jobPublicId());
            runner.run(claim);
        } catch (RuntimeException aborted) {
            // The lease lapses and the job is resumed later; the cause is a class name only, never a message.
            LOG.warn("Generation job aborted: jobId={}, errorType={}", claim.jobPublicId(),
                    aborted.getClass().getSimpleName());
        } finally {
            GenerationStop.unbind();
            // Also after an abort: the watchers reconnect and find the job resumed or ended.
            liveFeed.jobEnded(claim.jobPublicId());
            runningJobs.remove(claim.jobPublicId());
            running.decrementAndGet();
        }
    }

    private record RunningJob(FutureTask<Void> task, GenerationStop stop) {
    }
}
