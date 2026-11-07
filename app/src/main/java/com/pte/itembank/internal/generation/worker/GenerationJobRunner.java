package com.pte.itembank.internal.generation.worker;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.pipeline.GenerationItemStateService;
import com.pte.itembank.internal.generation.pipeline.GenerationPipeline;
import com.pte.itembank.internal.generation.pipeline.ItemOutcome;
import com.pte.itembank.internal.generation.pipeline.JobClaim;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Runs the items of a claimed job one after another. Before each item it checks that it still owns the job, that
 * nobody asked to cancel and renews the lease. A rejected key or a run of provider outages stops the whole job
 * instead of failing every remaining item against a provider that cannot answer.
 */
@Component
public class GenerationJobRunner {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationJobRunner.class);

    private final JobLeaseService leases;
    private final GenerationItemStateService itemState;
    private final GenerationPipeline pipeline;
    private final GenerationCallerResolver callers;
    private final QuestionGenerationProperties properties;

    public GenerationJobRunner(JobLeaseService leases, GenerationItemStateService itemState,
                               GenerationPipeline pipeline, GenerationCallerResolver callers,
                               QuestionGenerationProperties properties) {
        this.leases = leases;
        this.itemState = itemState;
        this.pipeline = pipeline;
        this.callers = callers;
        this.properties = properties;
    }

    public void run(JobClaim claim) {
        Optional<CurrentUser> caller = callers.resolve(claim.requesterId());
        if (caller.isEmpty()) {
            itemState.cancelOpen(claim);
            leases.finish(claim, JobStatus.CANCELLED, QuestionGenerationConstants.GENERATION_REQUESTER_NOT_ALLOWED);
            return;
        }
        int providerOutages = 0;
        List<Integer> pending = itemState.pendingIndexes(claim.jobId());
        for (int index : pending) {
            LeaseState state = leases.leaseState(claim);
            if (state == LeaseState.LOST || !leases.renewLease(claim)) {
                LOG.warn("Generation job lost its lease, stopping: jobId={}", claim.jobPublicId());
                return;
            }
            if (state == LeaseState.CANCEL_REQUESTED) {
                itemState.cancelOpen(claim);
                leases.finish(claim, JobStatus.CANCELLED, null);
                return;
            }
            ItemOutcome outcome = pipeline.runItem(claim, caller.get(), index);
            providerOutages = outcome == ItemOutcome.FAILED_PROVIDER_UNAVAILABLE ? providerOutages + 1 : 0;
            if (outcome == ItemOutcome.LEASE_LOST) {
                return;
            }
            if (outcome == ItemOutcome.CANCELLED) {
                itemState.cancelOpen(claim);
                leases.finish(claim, JobStatus.CANCELLED, null);
                return;
            }
            if (outcome == ItemOutcome.FAILED_PROVIDER_AUTH) {
                stop(claim, QuestionGenerationConstants.GENERATION_PROVIDER_AUTH);
                return;
            }
            if (providerOutages >= properties.getLimits().getConsecutiveProviderFailuresToStop()) {
                stop(claim, QuestionGenerationConstants.GENERATION_PROVIDER_UNAVAILABLE);
                return;
            }
        }
        leases.finish(claim, null, null);
    }

    private void stop(JobClaim claim, String failureCode) {
        itemState.failOpen(claim, failureCode);
        leases.finish(claim, JobStatus.FAILED, failureCode);
    }
}
