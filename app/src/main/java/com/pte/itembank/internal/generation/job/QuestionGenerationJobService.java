package com.pte.itembank.internal.generation.job;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ItemStage;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJob;
import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;
import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.SubmitGenerationJobRequest;
import com.pte.itembank.internal.generation.exception.GenerationDisabledException;
import com.pte.itembank.internal.generation.exception.GenerationJobConflictException;
import com.pte.itembank.internal.generation.exception.GenerationJobNotFoundException;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final GenerationTaskTypeCatalog catalog;
    private final QuestionGenerationProperties properties;
    private final Clock clock;

    public QuestionGenerationJobService(GenerationJobRepository jobs, GenerationJobItemRepository items,
                                        GenerationTaskTypeCatalog catalog, QuestionGenerationProperties properties,
                                        Clock clock) {
        this.jobs = jobs;
        this.items = items;
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
        CefrLevel cefr = parseCefr(request.cefrLevel());
        QuestionPool pool = parsePool(request.pool());
        String topicHint = TopicHintPolicy.normalize(request.topicHint());

        jobs.lockSubmissions(QuestionGenerationConstants.SUBMIT_ADVISORY_LOCK_KEY);
        checkCaps(caller, request.count());

        Instant now = clock.instant();
        QuestionGenerationJob job = new QuestionGenerationJob();
        job.setRequestedByUserPublicId(caller.userId());
        job.setTaskTypeKey(taskType.name());
        job.setCefrLevel(cefr);
        job.setPool(pool);
        job.setTopicHint(topicHint);
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
        Instant now = clock.instant();
        if (jobs.cancelQueued(publicId, now) > 0) {
            items.cancelOpenUnleased(job.getId(), now);
        } else if (jobs.markCancelling(publicId) == 0) {
            throw new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_NOT_CANCELLABLE,
                    QuestionGenerationConstants.GENERATION_JOB_NOT_CANCELLABLE_MESSAGE);
        }
        QuestionGenerationJob refreshed = jobs.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(GenerationJobNotFoundException::new);
        return GenerationJobResponse.from(refreshed, items.findByJobIdOrderByItemIndexAsc(refreshed.getId()));
    }

    private void checkCount(int count) {
        if (count < 1 || count > properties.getLimits().getMaxItemsPerJob()) {
            throw invalid(QuestionGenerationConstants.GENERATION_COUNT_OUT_OF_RANGE,
                    QuestionGenerationConstants.GENERATION_COUNT_OUT_OF_RANGE_MESSAGE);
        }
    }

    private void checkCaps(CurrentUser caller, int count) {
        QuestionGenerationProperties.Limits limits = properties.getLimits();
        if (jobs.existsByRequestedByUserPublicIdAndStatusIn(caller.userId(), JobStatus.ACTIVE)) {
            throw new GenerationJobConflictException(QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING,
                    QuestionGenerationConstants.GENERATION_JOB_ALREADY_RUNNING_MESSAGE);
        }
        Instant dayStart = clock.instant().truncatedTo(ChronoUnit.DAYS);
        if (jobs.sumRequestedSinceByUser(caller.userId(), dayStart) + count > limits.getMaxItemsPerUserPerDay()) {
            throw new GenerationLimitException(QuestionGenerationConstants.GENERATION_DAILY_LIMIT_REACHED,
                    QuestionGenerationConstants.GENERATION_DAILY_LIMIT_REACHED_MESSAGE);
        }
        if (jobs.sumRequestedSince(dayStart) + count > limits.getMaxItemsPerDayGlobal()) {
            throw new GenerationLimitException(QuestionGenerationConstants.GENERATION_GLOBAL_LIMIT_REACHED,
                    QuestionGenerationConstants.GENERATION_GLOBAL_LIMIT_REACHED_MESSAGE);
        }
        if (jobs.countByStatusIn(JobStatus.ACTIVE) >= limits.getMaxConcurrentJobs()) {
            throw new GenerationLimitException(QuestionGenerationConstants.GENERATION_TOO_MANY_JOBS,
                    QuestionGenerationConstants.GENERATION_TOO_MANY_JOBS_MESSAGE);
        }
    }

    private static CefrLevel parseCefr(String raw) {
        try {
            CefrLevel level = CefrLevel.parseOrNull(raw);
            if (level != null) {
                return level;
            }
        } catch (IllegalArgumentException unknown) {
            // falls through to the same error as a missing value
        }
        throw invalid(QuestionGenerationConstants.GENERATION_CEFR_INVALID,
                QuestionGenerationConstants.GENERATION_CEFR_INVALID_MESSAGE);
    }

    private static QuestionPool parsePool(String raw) {
        try {
            return QuestionPool.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid(QuestionGenerationConstants.GENERATION_POOL_INVALID,
                    QuestionGenerationConstants.GENERATION_POOL_INVALID_MESSAGE);
        }
    }

    private static GenerationRequestInvalidException invalid(String code, String message) {
        return new GenerationRequestInvalidException(code, message);
    }
}
