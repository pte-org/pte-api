package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.Question;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.JobStatus;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import com.pte.itembank.internal.generation.dto.GenerationMetaDto;
import com.pte.itembank.internal.generation.repository.GenerationJobItemRepository;
import com.pte.itembank.internal.generation.repository.GenerationJobRepository;
import com.pte.itembank.internal.generation.review.ReviewVerdict;
import com.pte.itembank.internal.repository.QuestionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Suppressed warnings for now, imports are used

/**
 * Saves one generated draft in ONE transaction: lease check, duplicate check, the normal {@link ItembankService}
 * creation (same validation as a hand-written question), the AI provenance columns and the SAVED mark on the job
 * item. A crash can therefore never leave a question without its item link, and a retry finds the slot filled.
 */
@Service
public class GeneratedQuestionWriter {

    private static final String DUPLICATE_NOTE = "The same content already exists for this task type.";

    private final ItembankService itembankService;
    private final QuestionRepository questions;
    private final GenerationJobRepository jobs;
    private final GenerationJobItemRepository items;
    private final QuestionGenerationProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final MetricsCalculator metricsCalculator;

    public GeneratedQuestionWriter(ItembankService itembankService, QuestionRepository questions,
                                   GenerationJobRepository jobs, GenerationJobItemRepository items,
                                   QuestionGenerationProperties properties, JsonMapper jsonMapper, Clock clock,
                                   MetricsCalculator metricsCalculator) {
        this.itembankService = itembankService;
        this.questions = questions;
        this.jobs = jobs;
        this.items = items;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.metricsCalculator = metricsCalculator;
    }

    /**
     * Quick duplicate check (outside transaction). Returns true if the item's stimulus text already exists
     * for this task type.
     */
    public boolean isDuplicate(JobClaim claim, GeneratedItem item) {
        String hash = ContentHasher.hash(item.stimulusText());
        return questions.existsByTaskTypeKeyAndContentHashAndDeletedFalse(claim.taskTypeKey(), hash);
    }

    @Transactional
    public ItemOutcome save(JobClaim claim, CurrentUser caller, int index, GeneratedItem item, ReviewVerdict verdict) {
        Instant now = clock.instant();
        List<JobStatus> held = jobs.statusIfLeaseHeld(claim.jobId(), claim.workerId());
        if (held.isEmpty()) {
            return ItemOutcome.LEASE_LOST;
        }
        if (held.get(0) == JobStatus.CANCELLING) {
            // The question in flight is discarded: cancelling means no more drafts appear.
            items.cancelOpen(claim.jobId(), claim.workerId(), now);
            return ItemOutcome.CANCELLED;
        }
        Optional<Question> existing = questions.findByGenerationJobPublicIdAndGenerationItemIndex(
                claim.jobPublicId(), index);
        if (existing.isPresent()) {
            return markSaved(claim, index, existing.get().getPublicId(), now);
        }
        String hash = ContentHasher.hash(item.stimulusText());
        if (questions.existsByTaskTypeKeyAndContentHashAndDeletedFalse(claim.taskTypeKey(), hash)) {
            items.fail(claim.jobId(), index, QuestionGenerationConstants.GENERATION_DUPLICATE_CONTENT,
                    DUPLICATE_NOTE, claim.workerId(), now);
            jobs.refreshCounters(claim.jobId());
            return ItemOutcome.FAILED;
        }
        QuestionResponse created = itembankService.create(item.question(), caller);
        Question question = questions.findByPublicIdForUpdate(created.publicId()).orElseThrow();
        question.setGenerationMeta(meta(claim, index, item, verdict));
        question.setContentHash(hash);
        question.setGenerationJobPublicId(claim.jobPublicId());
        question.setGenerationItemIndex(index);
        questions.save(question);
        return markSaved(claim, index, created.publicId(), now);
    }

    private ItemOutcome markSaved(JobClaim claim, int index, UUID questionPublicId, Instant now) {
        items.markSaved(claim.jobId(), index, questionPublicId, claim.workerId(), now);
        jobs.refreshCounters(claim.jobId());
        return ItemOutcome.SAVED;
    }

    private String meta(JobClaim claim, int index, GeneratedItem item, ReviewVerdict verdict) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema", QuestionGenerationConstants.META_SCHEMA_VERSION);
        meta.put("jobPublicId", claim.jobPublicId().toString());
        meta.put("itemIndex", index);
        meta.put("topic", item.topic());
        meta.put("source", item.source());
        meta.put("attempts", 1);
        meta.put("model", Map.of("generator", properties.getLlm().getGeneratorModel(),
                "reviewer", properties.getLlm().getReviewerModel()));
        meta.put("promptVersion", QuestionGenerationConstants.PROMPT_VERSION);
        if (verdict != null) {
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("verdict", verdict.verdict());
            review.put("checks", verdict.checks());
            review.put("fixSuggestion", verdict.fixSuggestion() == null ? "" : verdict.fixSuggestion());
            meta.put("review", review);
        }

        // Metrics from stimulus text
        if (item.stimulusText() != null) {
            GenerationMetaDto.GenerationMetricsDto metrics = metricsCalculator.calculate(item.stimulusText());
            meta.put("metrics", Map.of(
                    "words", metrics.words(),
                    "avgSentenceLength", metrics.avgSentenceLength(),
                    "fleschKincaid", metrics.fleschKincaid()));
        }

        // Key points from PassageDraft (for SWT, WE) or ChartDraft/DialogueDraft (phases 6, 7)
        if (item.draft() instanceof ItemDraft.PassageDraft passage && passage.keyPoints() != null) {
            meta.put("keyPoints", passage.keyPoints());
        } else if (item.draft() instanceof ItemDraft.ChartDraft chart && chart.keyPoints() != null) {
            meta.put("keyPoints", chart.keyPoints());
        } else if (item.draft() instanceof ItemDraft.DialogueDraft dialogue && dialogue.keyPoints() != null) {
            meta.put("keyPoints", dialogue.keyPoints());
        }

        // Where the stored audio or image came from (voice, speed, duration, chart type).
        if (item.mediaMeta() != null) {
            meta.put("media", item.mediaMeta());
        }

        return jsonMapper.writeValueAsString(meta);
    }
}
