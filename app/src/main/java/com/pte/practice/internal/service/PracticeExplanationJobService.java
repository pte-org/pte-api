package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeExplanation;
import com.pte.practice.internal.domain.PracticeExplanationJob;
import com.pte.practice.internal.domain.PracticeExplanationOutbox;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeExplanationJobStatus;
import com.pte.practice.internal.domain.enums.PracticeExplanationStatus;
import com.pte.practice.internal.exception.PracticeIdempotencyException;
import com.pte.practice.internal.repository.PracticeExplanationJobRepository;
import com.pte.practice.internal.repository.PracticeExplanationOutboxRepository;
import com.pte.practice.internal.repository.PracticeExplanationRepository;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.scoring.PracticeExplanationGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

/** Owns explanation job creation and provider orchestration. */
@Service
public class PracticeExplanationJobService {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeExplanationJobService.class);

    private final PracticeExplanationRepository explanationRepository;
    private final PracticeExplanationJobRepository jobRepository;
    private final PracticeExplanationOutboxRepository outboxRepository;
    private final PracticeSessionItemRepository itemRepository;
    private final ObjectProvider<PracticeExplanationGenerator> generatorProvider;
    private final PracticeExplanationJobTransitionService transitions;
    private final Clock clock;

    public PracticeExplanationJobService(PracticeExplanationRepository explanationRepository,
            PracticeExplanationJobRepository jobRepository, PracticeExplanationOutboxRepository outboxRepository,
            PracticeSessionItemRepository itemRepository, ObjectProvider<PracticeExplanationGenerator> generatorProvider,
            PracticeExplanationJobTransitionService transitions, Clock clock) {
        this.explanationRepository = explanationRepository;
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.itemRepository = itemRepository;
        this.generatorProvider = generatorProvider;
        this.transitions = transitions;
        this.clock = clock;
    }

    /** Called in the same transaction that creates the explanation snapshot. */
    @Transactional
    public PracticeExplanation enqueue(PracticeExplanation explanation, String idempotencyKey,
            String requestedModel) {
        Instant now = clock.instant();
        jobRepository.findByStudentPublicIdAndIdempotencyKeyAndDeletedFalse(
                        explanation.getStudentPublicId(), idempotencyKey)
                .filter(existing -> explanation.getId() == null || !existing.getExplanationId().equals(explanation.getId()))
                .ifPresent(existing -> {
                    throw PracticeIdempotencyException.reused();
                });
        if (explanation.getId() == null) {
            // The database requires a lifecycle status on the immutable
            // explanation snapshot. Set the initial queued state before the
            // first flush; the job metadata below is then persisted as the
            // same transaction advances the request.
            explanation.setStatus(PracticeExplanationStatus.QUEUED);
            explanation.setCanRetry(true);
            explanation.setAttemptCount(0);
            explanationRepository.saveAndFlush(explanation);
        }
        PracticeExplanationJob job = jobRepository.findByExplanationIdAndDeletedFalse(explanation.getId())
                .orElseGet(() -> createJob(explanation, idempotencyKey, now));
        if (job.getStatus() == PracticeExplanationJobStatus.AVAILABLE) {
            return explanation;
        }
        if (job.getStatus() == PracticeExplanationJobStatus.FAILED
                || job.getStatus() == PracticeExplanationJobStatus.CANCELLED) {
            job.setAttemptCount(0);
            job.setRunToken(UUID.randomUUID());
            job.setLastFailureCode(null);
            job.setLeaseToken(null);
            job.setLeaseUntil(null);
        }
        job.setStatus(PracticeExplanationJobStatus.QUEUED);
        job.setNextAttemptAt(now);
        if (job.getIdempotencyKey() == null) {
            job.setIdempotencyKey(idempotencyKey);
        }
        explanation.setStatus(PracticeExplanationStatus.QUEUED);
        explanation.setFailureCode(null);
        explanation.setCanRetry(true);
        explanation.setAttemptCount(job.getAttemptCount());
        explanation.setNextAttemptAt(now);
        explanation.setRequestedModel(requestedModel);
        explanation.setProviderVersion(PracticeExplanationGenerator.class.getName());
        jobRepository.saveAndFlush(job);
        explanationRepository.saveAndFlush(explanation);

        PracticeExplanationOutbox outbox = new PracticeExplanationOutbox();
        outbox.setJobId(job.getId());
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outboxRepository.save(outbox);
        return explanation;
    }

    /** Provider work runs outside the claim transaction. */
    public void process(UUID jobPublicId) {
        PracticeExplanationJobTransitionService.JobClaim claim = transitions.claim(jobPublicId);
        if (claim == null) {
            return;
        }
        PracticeExplanation explanation = explanationRepository.findByIdAndDeletedFalse(claim.explanationId())
                .orElse(null);
        if (explanation == null) {
            transitions.fail(claim, PracticeConstants.PRACTICE_EXPLANATION_FAILURE_INTERNAL);
            return;
        }
        PracticeSessionItem item = itemRepository.findByIdAndDeletedFalse(explanation.getPracticeSessionItemId())
                .orElse(null);
        PracticeExplanationGenerator generator = generatorProvider.getIfAvailable();
        if (item == null || generator == null) {
            transitions.fail(claim, PracticeConstants.PRACTICE_EXPLANATION_FAILURE_TERMINAL);
            return;
        }
        if (item.getRubricRevisionPublicId() == null || item.getRubricRevisionNumber() == null
                || item.getRubricCanonicalHash() == null || item.getRubricCanonicalHash().isBlank()
                || item.getRubricSnapshotJson() == null || item.getRubricSnapshotJson().isBlank()
                || item.getRuntimeCompatibilityHash() == null || item.getRuntimeCompatibilityHash().isBlank()) {
            transitions.fail(claim, PracticeConstants.PRACTICE_EXPLANATION_RUBRIC_REQUIRED);
            return;
        }
        try {
            EvidenceManifest evidenceManifest = availableEvidenceManifest(item);
            PracticeExplanationGenerator.GeneratedExplanation generated = generator.generate(
                    new PracticeExplanationGenerator.ExplanationRequest(item.getTaskCode(), item.getPromptText(),
                            item.getSavedPayload(), item.getReferenceAnswerText(), item.getCorrectAnswerText(),
                            item.getOptionsJson(), explanation.getLanguage(), explanation.getRubricVersion(),
                            item.getRubricRevisionPublicId() == null ? null
                                    : new PracticeExplanationGenerator.RubricContext(
                                            item.getRubricRevisionPublicId(),
                                            item.getRubricRevisionNumber() == null ? 0 : item.getRubricRevisionNumber(),
                                            item.getRubricCanonicalHash(), item.getRubricSnapshotJson(),
                                            item.getRuntimeCompatibilityHash()),
                            item.getObjectiveResultHash() == null ? null
                                    : new PracticeExplanationGenerator.ObjectiveResultContext(
                                            item.getObjectiveScore(), item.getObjectiveEvaluatorVersion(),
                                            item.getObjectiveResultHash(), item.getObjectiveEvidenceIdentityHash()),
                            evidenceManifest.types(), evidenceManifest.paths(),
                            formResult(item)));
            transitions.complete(claim, generated);
        } catch (RuntimeException failure) {
            LOG.warn("Practice explanation generation failed: errorType={}, reason={}",
                    failure.getClass().getSimpleName(), safeReason(failure));
            transitions.fail(claim, PracticeConstants.PRACTICE_EXPLANATION_FAILURE_RETRYABLE);
        }
    }

    public void recoverExpired() {
        transitions.recoverExpired();
    }

    public void markDeadLettered(UUID jobPublicId, UUID publishedRunToken, Integer publishedAttemptCount) {
        transitions.markDeadLettered(jobPublicId, publishedRunToken, publishedAttemptCount);
    }

    private EvidenceManifest availableEvidenceManifest(PracticeSessionItem item) {
        Set<String> types = new LinkedHashSet<>();
        Set<String> paths = new LinkedHashSet<>();
        addPromptEvidence(item, types, paths);
        if (item.getSavedPayload() != null && !item.getSavedPayload().isBlank()) {
            types.add("RESPONSE");
            paths.add("submitted_response.response");
            if (isTextResponse(item)) {
                types.add("RESPONSE_TEXT");
                paths.add("submitted_response.text");
            }
        }
        if (item.getAiTranscript() != null && !item.getAiTranscript().isBlank()) {
            types.add("RESPONSE_TRANSCRIPT");
            paths.add("scoring_result.transcript");
        }
        if (item.getAiScore() != null) {
            types.add("SPEECH_RESULT");
            paths.addAll(Set.of("scoring_result.score", "scoring_result.sub_scores"));
        }
        addReferenceEvidence(item, types, paths);
        if (item.getObjectiveResultHash() != null && !item.getObjectiveResultHash().isBlank()) {
            types.addAll(Set.of("DETERMINISTIC_RESULT", "OBJECTIVE_RESULT"));
            paths.addAll(Set.of("objective_result.result", "objective_result.status"));
        }
        if (item.getFormResultHash() != null && !item.getFormResultHash().isBlank()) {
            types.add("FORM_RESULT");
            paths.addAll(Set.of("form_result.result", "form_result.status", "form_result.word_count"));
        }
        return new EvidenceManifest(Set.copyOf(types), Set.copyOf(paths));
    }

    private void addPromptEvidence(PracticeSessionItem item, Set<String> types, Set<String> paths) {
        if (item.getPromptText() == null || item.getPromptText().isBlank()) return;
        switch (item.getRendererKey()) {
            case "PERSONAL_INTRODUCTION_V1", "WRITE_ESSAY_V1" -> {
                types.add("PROMPT");
                paths.add("task_prompt.prompt");
            }
            case "READ_ALOUD_V1" -> {
                types.add("PROMPT_TEXT");
                paths.add("task_prompt.text");
            }
            case "SUMMARIZE_WRITTEN_TEXT_V1", "MC_READING_SINGLE_V1", "MC_READING_MULTIPLE_V1",
                    "FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1", "FILL_IN_THE_BLANKS_DROPDOWN_V1" -> {
                types.add("PASSAGE");
                paths.add("task_prompt.passage");
            }
            case "RE_ORDER_PARAGRAPHS_V1" -> {
                types.add("PARAGRAPH_BLOCKS");
                paths.add("task_prompt.paragraph_blocks");
            }
            default -> { }
        }
    }

    private void addReferenceEvidence(PracticeSessionItem item, Set<String> types, Set<String> paths) {
        if (item.getCorrectAnswerText() != null && !item.getCorrectAnswerText().isBlank()) {
            if ("RE_ORDER_PARAGRAPHS_V1".equals(item.getRendererKey())) {
                types.add("ANSWER_ORDER");
                paths.add("reference.answer_order");
            } else {
                types.add("ANSWER_KEY");
                paths.add("reference.answer_key");
            }
        }
        if (item.getOptionsJson() != null && !item.getOptionsJson().isBlank()) {
            if ("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1".equals(item.getRendererKey())) {
                types.add("WORD_BANK");
                paths.add("reference.word_bank");
            } else {
                types.add("OPTION_SET");
                paths.add("reference.option_set");
            }
        }
        if (item.getReferenceAnswerText() != null && !item.getReferenceAnswerText().isBlank()
                && Set.of("REPEAT_SENTENCE_V1", "RE_TELL_LECTURE_V1", "SUMMARIZE_SPOKEN_TEXT_V1")
                        .contains(item.getRendererKey())) {
            types.add("SOURCE_TRANSCRIPT");
            paths.add("reference.source_transcript");
        }
    }

    private PracticeExplanationGenerator.FormResultContext formResult(PracticeSessionItem item) {
        return item.getFormResultHash() == null ? null : new PracticeExplanationGenerator.FormResultContext(
                item.getFormWordCount(), item.getFormStatus(), item.getFormEvaluatorVersion(),
                item.getFormResultHash(), item.getFormEvidenceIdentityHash());
    }

    private boolean isTextResponse(PracticeSessionItem item) {
        return "SUMMARIZE_WRITTEN_TEXT_V1".equals(item.getRendererKey())
                || "WRITE_ESSAY_V1".equals(item.getRendererKey());
    }

    private String safeReason(RuntimeException failure) {
        Throwable current = failure;
        String reason = null;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.matches("[A-Za-z0-9 _.,:;()\\-]{1,160}")) {
                reason = message;
            }
            current = current.getCause();
        }
        return reason == null ? "UNCLASSIFIED" : reason;
    }

    private record EvidenceManifest(Set<String> types, Set<String> paths) { }

    private PracticeExplanationJob createJob(PracticeExplanation explanation, String idempotencyKey, Instant now) {
        PracticeExplanationJob job = new PracticeExplanationJob();
        job.setExplanationId(explanation.getId());
        job.setStudentPublicId(explanation.getStudentPublicId());
        job.setLanguage(explanation.getLanguage());
        job.setGenerationSequence(explanation.getGenerationSequence());
        job.setStatus(PracticeExplanationJobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setNextAttemptAt(now);
        job.setIdempotencyKey(idempotencyKey);
        job.setRunToken(UUID.randomUUID());
        return saveJob(job);
    }

    private PracticeExplanationJob saveJob(PracticeExplanationJob job) {
        try {
            return jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException conflict) {
            throw PracticeIdempotencyException.reused();
        }
    }
}
