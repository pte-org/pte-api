package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Short transactions that move one item (or all open items of a job) between stages. Every write is guarded by the
 * job lease, so a worker that lost its lease changes nothing.
 */
@Service
public class GenerationItemStateService {

    private final GenerationJobItemRepository items;
    private final GenerationJobRepository jobs;
    private final Clock clock;

    public GenerationItemStateService(GenerationJobItemRepository items, GenerationJobRepository jobs, Clock clock) {
        this.items = items;
        this.jobs = jobs;
        this.clock = clock;
    }

    /** Indexes of the items still to do, in order. */
    @Transactional(readOnly = true)
    public List<Integer> pendingIndexes(Long jobId) {
        return items.findByJobIdOrderByItemIndexAsc(jobId).stream()
                .filter(item -> !item.getStage().isTerminal())
                .map(QuestionGenerationJobItem::getItemIndex)
                .toList();
    }

    /** @return false when the lease was lost (or the item already finished), so the caller must stop */
    @Transactional
    public boolean advance(JobClaim claim, int index, ItemStage stage) {
        return items.advance(claim.jobId(), index, stage, claim.workerId(), clock.instant()) > 0;
    }

    /** True when someone asked to stop the job while this worker holds it; checked before each rewrite. */
    @Transactional(readOnly = true)
    public boolean cancelRequested(JobClaim claim) {
        return jobs.statusIfLeaseHeld(claim.jobId(), claim.workerId()).contains(JobStatus.CANCELLING);
    }

    /** The question went into the form it was asked from; nothing is saved. */
    @Transactional
    public void ready(JobClaim claim, int index) {
        items.markReady(claim.jobId(), index, claim.workerId(), clock.instant());
        jobs.refreshCounters(claim.jobId());
    }

    @Transactional
    public void fail(JobClaim claim, int index, String code, String note) {
        items.fail(claim.jobId(), index, code, truncate(note), claim.workerId(), clock.instant());
        jobs.refreshCounters(claim.jobId());
    }

    @Transactional
    public void cancelOpen(JobClaim claim) {
        items.cancelOpen(claim.jobId(), claim.workerId(), clock.instant());
        jobs.refreshCounters(claim.jobId());
    }

    @Transactional
    public void failOpen(JobClaim claim, String code) {
        items.failOpen(claim.jobId(), code, claim.workerId(), clock.instant());
        jobs.refreshCounters(claim.jobId());
    }

    private static String truncate(String note) {
        if (note == null || note.length() <= QuestionGenerationConstants.MAX_FAILURE_NOTE_LENGTH) {
            return note;
        }
        return note.substring(0, QuestionGenerationConstants.MAX_FAILURE_NOTE_LENGTH);
    }
}
