package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
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
    private final QuestionGenerationProperties properties;
    private final Clock clock;

    public JobLeaseService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                           QuestionGenerationProperties properties, Clock clock) {
        this.jobs = jobs;
        this.items = items;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Takes the oldest queued job, or a running one whose lease expired (its worker died). A cancel request whose
     * worker died is closed here instead of resumed.
     */
    @Transactional
    public Optional<JobClaim> claimNext(String workerId) {
        Instant now = clock.instant();
        List<Long> ids = jobs.lockNextClaimable(now);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        QuestionGenerationJob job = jobs.findById(ids.get(0)).orElseThrow();
        if (job.getStatus() == JobStatus.CANCELLING) {
            items.cancelOpenUnleased(job.getId(), now);
            jobs.finishStaleCancelling(job.getId(), now);
            jobs.refreshCounters(job.getId());
            return Optional.empty();
        }
        jobs.markRunning(job.getId(), workerId, leaseEnd(now), now);
        return Optional.of(new JobClaim(job.getId(), job.getPublicId(), job.getRequestedByUserPublicId(),
                job.getTaskTypeKey(), job.getCefrLevel(), job.getPool(), job.getTopicHint(), workerId));
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
     * the job is COMPLETED if at least one question was saved and FAILED if none was.
     */
    @Transactional
    public void finish(JobClaim claim, JobStatus requested, String failureCode) {
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
        jobs.finish(claim.jobId(), claim.workerId(), status, failureCode, clock.instant());
    }

    private Instant leaseEnd(Instant now) {
        return now.plus(properties.getWorker().getLeaseDuration());
    }
}
