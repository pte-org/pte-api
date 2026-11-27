package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.BatchGenerationConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.BatchStatus;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.SubmitGenerationJobRequest;
import com.pte.itembank.internal.generation.exception.GenerationDisabledException;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.repository.GenerationBatchRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.worker.GenerationCancelRequested;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accepts and cancels generation jobs. A submit runs every cap check and the insert in ONE transaction behind a
 * database advisory lock, and the one-active-job rule is also a partial unique index, so concurrent requests
 * cannot pass a check-then-insert together. The quota day is the UTC day and counts requested questions.
 */
@Service
public class QuestionGenerationJobService {

    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final GenerationBatchRepository batches;
    private final GenerationTaskTypeCatalog catalog;
    private final QuestionGenerationProperties properties;
    private final Clock clock;
    private final GenerationLimits limits;
    private final GenerationQuotaGuard quota;
    private final ApplicationEventPublisher events;

    public QuestionGenerationJobService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                                        GenerationBatchRepository batches, GenerationTaskTypeCatalog catalog,
                                        QuestionGenerationProperties properties, GenerationLimits limits,
                                        GenerationQuotaGuard quota, ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.limits = limits;
        this.quota = quota;
        this.jobs = jobs;
        this.items = items;
        this.batches = batches;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public GenerationJobResponse submit(SubmitGenerationJobRequest request, CurrentUser caller) {
        GenerationAccessPolicy.requireAuthor(caller);
        if (!properties.isEnabled()) {
            throw new GenerationDisabledException();
        }
        PteTaskType taskType = catalog.find(request.taskType()).orElseThrow(() -> invalid(
                QuestionGenerationConstants.GENERATION_TASK_TYPE_UNSUPPORTED,
                QuestionGenerationConstants.GENERATION_TASK_TYPE_UNSUPPORTED_MESSAGE));
        checkCount(request.count());
        CefrLevel cefr = GenerationRequestFields.cefr(request.cefrLevel());
        QuestionPool pool = GenerationRequestFields.pool(request.pool());
        String topicHint = TopicHintPolicy.normalize(request.topicHint());
        ImageKind imageKind = GenerationRequestFields.imageKind(request.imageKind(), properties.isPictureEnabled());

        jobs.lockSubmissions(QuestionGenerationConstants.SUBMIT_ADVISORY_LOCK_KEY);
        checkCaps(caller, request.count(), request.savesQuestions());

        Instant now = clock.instant();
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(caller.userId());
        job.setTaskTypeKey(taskType.name());
        job.setCefrLevel(cefr);
        job.setPool(pool);
        job.setTopicHint(topicHint);
        job.setImageKind(imageKind);
        job.setRequestedCount(request.count());
        job.setSaveQuestions(request.savesQuestions());
        QuestionGenerationJob saved;
        try {
            saved = jobs.saveAndFlush(job);
        } catch (DataIntegrityViolationException raced) {
            throw new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING,
                    QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING_MESSAGE);
        }
        List<QuestionGenerationJobItem> created = new ArrayList<>();
        for (int index = 0; index < request.count(); index++) {
            QuestionGenerationJobItem item = new QuestionGenerationJobItem();
            item.setJobId(saved.getId());
            item.setItemIndex(index);
            item.setStage(ItemStage.QUEUED);
            item.setUpdatedAt(now);
            created.add(item);
        }
        return GenerationJobResponse.from(saved, items.saveAll(created));
    }

    /**
     * Cancels a queued job at once, or asks the running worker to stop (CANCELLING). The question being made when
     * the request arrives is discarded; questions already saved stay.
     */
    @Transactional
    public GenerationJobResponse cancel(UUID publicId, CurrentUser caller) {
        QuestionGenerationJob job = jobs.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationJobNotFoundException::new);
        GenerationAccessPolicy.requireAccess(caller, job);
        if (job.getBatchId() != null) {
            // A child cancelled alone would leave its batch to promote the next one; the batch is the unit to cancel.
            throw new GenerationJobConflictException(BatchGenerationConstants.GENERATION_JOB_PART_OF_BATCH,
                    BatchGenerationConstants.GENERATION_JOB_PART_OF_BATCH_MESSAGE);
        }
        Instant now = clock.instant();
        if (jobs.cancelQueued(publicId, now) > 0) {
            items.cancelOpenUnleased(job.getId(), now);
        } else if (jobs.markCancelling(publicId) == 0) {
            throw new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_NOT_CANCELLABLE,
                    QuestionGenerationConstants.GENERATION_JOB_NOT_CANCELLABLE_MESSAGE);
        } else {
            events.publishEvent(new GenerationCancelRequested(publicId));
        }
        QuestionGenerationJob refreshed = jobs.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationJobNotFoundException::new);
        return GenerationJobResponse.from(refreshed, items.findByJobIdOrderByItemIndexAsc(refreshed.getId()));
    }

    private void checkCount(int count) {
        if (count < 1 || count > limits.maxItemsPerJob()) {
            throw invalid(QuestionGenerationConstants.GENERATION_COUNT_OUT_OF_RANGE,
                    QuestionGenerationConstants.GENERATION_COUNT_OUT_OF_RANGE_MESSAGE);
        }
    }

    /**
     * One open saving unit per writer: a saving job, or a batch (whose children are saving jobs). A preview job from
     * the question form saves nothing and is exempt, so the form keeps working while a batch runs.
     */
    private void checkCaps(CurrentUser caller, int count, boolean savesQuestions) {
        if (savesQuestions && (batches.existsByRequestedByUserPublicIdAndStatusIn(caller.userId(), BatchStatus.OPEN)
                || jobs.existsByRequestedByUserPublicIdAndSaveQuestionsTrueAndStatusIn(caller.userId(),
                JobStatus.OPEN))) {
            throw new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING,
                    QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING_MESSAGE);
        }
        quota.requireWithin(caller, count);
        // No concurrency check here: a busy system leaves the job QUEUED; the cap is enforced at claim.
    }

    private static GenerationRequestInvalidException invalid(String code, String message) {
        return new GenerationRequestInvalidException(code, message);
    }
}
