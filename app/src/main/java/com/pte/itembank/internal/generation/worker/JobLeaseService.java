package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.internal.generation.batch.BatchChainingService;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The only place a worker takes, keeps and gives back a job. Every method is one short transaction: the job is
 * owned through {@code worker_id} plus {@code lease_until}, never through a lock held while a provider is called.
 */
@Service
public class JobLeaseService {

    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationBatchRepository batches;
    private final GenerationLimits limits;
    private final QuestionGenerationProperties properties;
    private final BatchChainingService chaining;
    private final Clock clock;

    public JobLeaseService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                           GenerationBatchRepository batches, GenerationLimits limits,
                           QuestionGenerationProperties properties, BatchChainingService chaining, Clock clock) {
        this.chaining = chaining;
        this.jobs = jobs;
        this.items = items;
        this.batches = batches;
        this.limits = limits;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Takes a job for this worker, under the claim advisory lock so the concurrency cap holds across instances.
     * A job whose worker died is always resumed first, even at the cap (it replaces a dead lease); a cancel request
     * whose worker died is closed instead. Otherwise, below the cap, the next queued job is taken: standalone and
     * preview jobs before batch children.
     */
    @Transactional
    public Optional<JobClaim> claimNext(String workerId) {
        Instant now = clock.instant();
        jobs.lockClaims(QuestionGenerationConstants.CLAIM_ADVISORY_LOCK_KEY);
        List<Long> resumable = jobs.findNextResumable(now);
        if (!resumable.isEmpty()) {
            return resume(resumable.get(0), workerId, now);
        }
        if (jobs.countLiveLeases(now) >= limits.maxConcurrentJobs()) {
            return Optional.empty();
        }
        List<Long> queued = jobs.findNextQueued();
        if (queued.isEmpty()) {
            return Optional.empty();
        }
        Long batchId = lockBatchOf(queued.get(0));
        QuestionGenerationJob job = jobs.findById(queued.get(0)).orElseThrow();
        // Guarded: a cancel that won since the select leaves the job not QUEUED, so nothing is claimed.
        if (job.getStatus() != JobStatus.QUEUED || jobs.markRunning(job.getId(), workerId, leaseEnd(now), now) == 0) {
            return Optional.empty();
        }
        if (batchId != null) {
            batches.markRunning(batchId, now);
        }
        return Optional.of(claimOf(job, workerId));
    }

    /** Separate transaction on purpose: the renewal commits before the slow provider call that follows it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewLease(JobClaim claim) {
        return jobs.renewLease(claim.jobId(), claim.workerId(), leaseEnd(clock.instant())) > 0;
    }

    @Transactional(readOnly = true)
    public LeaseState leaseState(JobClaim claim) {
        List<JobStatus> held = jobs.statusIfLeaseHeld(claim.jobId(), claim.workerId());
        if (held.isEmpty()) {
            return LeaseState.LOST;
        }
        return held.get(0) == JobStatus.CANCELLING ? LeaseState.CANCEL_REQUESTED : LeaseState.HELD;
    }

    /**
     * Closes the job. A pending cancel request always wins; otherwise {@code requested} is used, and when it is null
     * the job is COMPLETED if at least one question was saved and FAILED if none was. A batch child locks its batch
     * row first and, only when its guarded update succeeded (a lost lease never chains), lets the batch move on.
     */
    @Transactional
    public void finish(JobClaim claim, JobStatus requested, String failureCode) {
        Long batchId = lockBatchOf(claim.jobId());
        jobs.refreshCounters(claim.jobId());
        QuestionGenerationJob job = jobs.findById(claim.jobId()).orElseThrow();
        JobStatus status;
        if (job.getStatus() == JobStatus.CANCELLING) {
            status = JobStatus.CANCELLED;
        } else if (requested != null) {
            status = requested;
        } else {
            status = job.getSucceededCount() > 0 ? JobStatus.COMPLETED : JobStatus.FAILED;
        }
        if (jobs.finish(claim.jobId(), claim.workerId(), status, failureCode, clock.instant()) == 1
                && batchId != null) {
            chaining.onChildTerminal(batchId, claim.jobId());
        }
    }

    private Optional<JobClaim> resume(Long jobId, String workerId, Instant now) {
        QuestionGenerationJob job = jobs.findById(jobId).orElseThrow();
        if (job.getStatus() == JobStatus.CANCELLING) {
            Long batchId = lockBatchOf(jobId);
            items.cancelOpenUnleased(jobId, now);
            int closed = jobs.finishStaleCancelling(jobId, now);
            jobs.refreshCounters(jobId);
            if (closed == 1 && batchId != null) {
                chaining.onChildTerminal(batchId, jobId);
            }
            return Optional.empty();
        }
        if (jobs.markRunning(job.getId(), workerId, leaseEnd(now), now) == 0) {
            return Optional.empty();
        }
        return Optional.of(claimOf(job, workerId));
    }

    /**
     * Locks the batch row of a batch child and returns its id (null for a standalone job). Batch row first, then the
     * job rows, on every path (claim, finish, stale cancel, batch cancel), so they cannot deadlock.
     */
    private Long lockBatchOf(Long jobId) {
        List<Long> found = jobs.findBatchIdById(jobId);
        Long batchId = found.isEmpty() ? null : found.get(0);
        if (batchId != null) {
            batches.findByIdForUpdate(batchId);
        }
        return batchId;
    }

    private static JobClaim claimOf(QuestionGenerationJob job, String workerId) {
        return new JobClaim(job.getId(), job.getPublicId(), job.getRequestedByUserPublicId(), job.getTaskTypeKey(),
                job.getCefrLevel(), job.getPool(), job.getTopicHint(), workerId, job.isSaveQuestions(),
                job.getImageKind());
    }

    private Instant leaseEnd(Instant now) {
        return now.plus(properties.getWorker().getLeaseDuration());
    }
}
