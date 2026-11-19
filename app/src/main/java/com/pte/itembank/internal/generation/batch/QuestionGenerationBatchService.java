package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.batch.BatchRequestValidator.ValidatedBatch;
import com.pte.itembank.internal.generation.batch.BatchSplitter.ChildPlan;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.batch.dto.SubmitGenerationBatchRequest;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationBatch;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.job.GenerationQuotaGuard;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Accepts a batch: the batch, every child job (seq 0 QUEUED, the rest PENDING) and every item are created in ONE
 * transaction behind the same advisory lock as a single job submit, so the one-open-unit rule and the daily quotas
 * cannot be passed by concurrent requests. The whole batch is counted against the quota at once.
 */
@Service
public class QuestionGenerationBatchService {

    private final BatchRequestValidator validator;
    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationBatchRepository batches;
    private final GenerationLimits limits;
    private final GenerationQuotaGuard quota;
    private final AuditLogService audit;
    private final QuestionGenerationBatchQueryService queries;
    private final Clock clock;

    public QuestionGenerationBatchService(BatchRequestValidator validator, GenerationJobRepository jobs,
                                          GenerationJobItemRepository items, GenerationBatchRepository batches,
                                          GenerationLimits limits, AuditLogService audit,
                                          QuestionGenerationBatchQueryService queries, GenerationQuotaGuard quota,
                                          Clock clock) {
        this.quota = quota;
        this.queries = queries;
        this.validator = validator;
        this.jobs = jobs;
        this.items = items;
        this.batches = batches;
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public GenerationBatchResponse submit(SubmitGenerationBatchRequest request, CurrentUser caller) {
        ValidatedBatch valid = validator.validate(request, caller);

        jobs.lockSubmissions(QuestionGenerationConstants.SUBMIT_ADVISORY_LOCK_KEY);
        requireNoOpenUnit(caller);
        quota.requireWithin(caller, valid.total());

        Instant now = clock.instant();
        List<ChildPlan> plans = BatchSplitter.split(valid.rows(), limits.maxItemsPerJob());
        QuestionGenerationBatch batch = new QuestionGenerationBatch();
        batch.setRequestedByUserPublicId(caller.userId());
        batch.setPool(valid.pool());
        batch.setRows(valid.rows());
        batch.setTotalRequested(valid.total());
        QuestionGenerationBatch saved;
        List<QuestionGenerationJob> children;
        try {
            saved = batches.saveAndFlush(batch);
            children = jobs.saveAllAndFlush(plans.stream().map(plan -> child(saved, valid, plan)).toList());
        } catch (DataIntegrityViolationException raced) {
            throw alreadyRunning();
        }
        items.saveAll(itemsOf(children, now));
        audit.record(caller, BatchGenerationConstants.BATCH_AGGREGATE, saved.getPublicId().toString(),
                BatchGenerationConstants.BATCH_CREATED, String.format(BatchGenerationConstants.BATCH_CREATED_SUMMARY,
                        valid.rows().size(), valid.total(), children.size()));
        return GenerationBatchResponse.detail(saved, children, Map.of(ItemStage.QUEUED, (long) valid.total()));
    }

    /**
     * Stops a batch. Idempotent: a batch already cancelling or cancelled answers its current state. Under the batch
     * row lock (batch first, then jobs, like the worker), the waiting children are cancelled FIRST, then the running
     * child is asked to stop; its finish hook closes the batch. Questions already saved stay.
     */
    @Transactional
    public GenerationBatchResponse cancel(UUID publicId, CurrentUser caller) {
        Long batchId = queries.require(publicId, caller).getId();
        batches.findByIdForUpdate(batchId);
        Instant now = clock.instant();
        // Decided by the guarded update, not by the entity read before the lock (it may be stale).
        if (batches.requestCancel(batchId, now) == 0) {
            BatchStatus current = batches.findById(batchId).orElseThrow().getStatus();
            if (current == BatchStatus.CANCELLING || current == BatchStatus.CANCELLED) {
                return queries.detail(batchId);
            }
            throw new GenerationJobConflictException(BatchGenerationConstants.GENERATION_BATCH_NOT_CANCELLABLE,
                    BatchGenerationConstants.GENERATION_BATCH_NOT_CANCELLABLE_MESSAGE);
        }
        items.cancelOpenUnleasedByBatch(batchId, now);
        jobs.cancelPending(batchId, now, null);
        jobs.findByBatchIdOrderByBatchSeqAsc(batchId).stream()
                .filter(child -> JobStatus.LEASED.contains(child.getStatus()))
                .forEach(child -> jobs.markCancelling(child.getPublicId()));
        batches.refreshCounters(batchId, now);
        if (!jobs.existsByBatchIdAndStatusIn(batchId, JobStatus.OPEN)) {
            batches.close(batchId, BatchStatus.CANCELLED, null, now);
        }
        audit.record(caller, BatchGenerationConstants.BATCH_AGGREGATE, publicId.toString(),
                BatchGenerationConstants.BATCH_CANCELLED,
                String.format(BatchGenerationConstants.BATCH_CANCELLED_SUMMARY,
                        batches.findById(batchId).orElseThrow().getStatus()));
        return queries.detail(batchId);
    }

    /** One open saving unit per writer: an open batch, or an open job that saves to the bank (previews are exempt). */
    private void requireNoOpenUnit(CurrentUser caller) {
        if (batches.existsByRequestedByUserPublicIdAndStatusIn(caller.userId(), BatchStatus.OPEN)
                || jobs.existsByRequestedByUserPublicIdAndSaveQuestionsTrueAndStatusIn(caller.userId(),
                JobStatus.OPEN)) {
            throw alreadyRunning();
        }
    }

    /** Only the first child is claimable; the others wait PENDING until the previous one finishes. */
    private static QuestionGenerationJob child(QuestionGenerationBatch batch, ValidatedBatch valid, ChildPlan plan) {
        BatchRow row = valid.rows().get(plan.rowIndex());
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(batch.getRequestedByUserPublicId());
        job.setTaskTypeKey(row.taskType());
        job.setCefrLevel(row.cefrLevel());
        job.setPool(valid.pool());
        job.setTopicHint(plan.topicHint());
        job.setImageKind(row.imageKind());
        job.setRequestedCount(plan.count());
        job.setSaveQuestions(true);
        job.setStatus(plan.seq() == 0 ? JobStatus.QUEUED : JobStatus.PENDING);
        job.setBatchId(batch.getId());
        job.setBatchSeq(plan.seq());
        job.setBatchRowIndex(plan.rowIndex());
        return job;
    }

    private static List<QuestionGenerationJobItem> itemsOf(List<QuestionGenerationJob> children, Instant now) {
        List<QuestionGenerationJobItem> created = new ArrayList<>();
        for (QuestionGenerationJob child : children) {
            for (int index = 0; index < child.getRequestedCount(); index++) {
                QuestionGenerationJobItem item = new QuestionGenerationJobItem();
                item.setJobId(child.getId());
                item.setItemIndex(index);
                item.setStage(ItemStage.QUEUED);
                item.setUpdatedAt(now);
                created.add(item);
            }
        }
        return created;
    }

    private static GenerationJobConflictException alreadyRunning() {
        return new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING,
                QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING_MESSAGE);
    }
}
