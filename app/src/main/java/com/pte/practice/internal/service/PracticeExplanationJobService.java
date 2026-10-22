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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Owns explanation job creation and provider orchestration. */
@Service
public class PracticeExplanationJobService {

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
        try {
            PracticeExplanationGenerator.GeneratedExplanation generated = generator.generate(
                    new PracticeExplanationGenerator.ExplanationRequest(item.getTaskCode(), item.getPromptText(),
                            item.getSavedPayload(), item.getReferenceAnswerText(), item.getCorrectAnswerText(),
                            item.getOptionsJson(), explanation.getLanguage(), explanation.getRubricVersion()));
            transitions.complete(claim, generated);
        } catch (RuntimeException failure) {
            transitions.fail(claim, PracticeConstants.PRACTICE_EXPLANATION_FAILURE_RETRYABLE);
        }
    }

    public void recoverExpired() {
        transitions.recoverExpired();
    }

    public void markDeadLettered(UUID jobPublicId, UUID publishedRunToken, Integer publishedAttemptCount) {
        transitions.markDeadLettered(jobPublicId, publishedRunToken, publishedAttemptCount);
    }

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
