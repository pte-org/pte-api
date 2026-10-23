package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeExplanation;
import com.pte.practice.internal.domain.PracticeExplanationJob;
import com.pte.practice.internal.domain.PracticeExplanationOutbox;
import com.pte.practice.internal.domain.enums.PracticeExplanationJobStatus;
import com.pte.practice.internal.domain.enums.PracticeExplanationStatus;
import com.pte.practice.internal.repository.PracticeExplanationJobRepository;
import com.pte.practice.internal.repository.PracticeExplanationOutboxRepository;
import com.pte.practice.internal.repository.PracticeExplanationRepository;
import com.pte.scoring.PracticeAiProperties;
import com.pte.scoring.PracticeExplanationGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Persists the fenced state transitions of one durable explanation job. */
@Service
public class PracticeExplanationJobTransitionService {

    private final PracticeExplanationRepository explanationRepository;
    private final PracticeExplanationJobRepository jobRepository;
    private final PracticeExplanationOutboxRepository outboxRepository;
    private final ObjectProvider<PracticeAiProperties> propertiesProvider;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PracticeExplanationJobTransitionService(PracticeExplanationRepository explanationRepository,
            PracticeExplanationJobRepository jobRepository, PracticeExplanationOutboxRepository outboxRepository,
            ObjectProvider<PracticeAiProperties> propertiesProvider, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.explanationRepository = explanationRepository;
        this.jobRepository = jobRepository;
        this.outboxRepository = outboxRepository;
        this.propertiesProvider = propertiesProvider;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Claims a due job with a DB lease before any provider call is made. */
    public JobClaim claim(UUID jobPublicId) {
        return transactions.execute(status -> claimInTransaction(jobPublicId));
    }

    public void complete(JobClaim claim, PracticeExplanationGenerator.GeneratedExplanation generated) {
        transactions.execute(status -> {
            completeInTransaction(claim, generated);
            return null;
        });
    }

    public void fail(JobClaim claim, String failureCode) {
        transactions.execute(status -> {
            failInTransaction(claim, failureCode, false);
            return null;
        });
    }

    public void recoverExpired() {
        transactions.execute(status -> {
            recoverExpiredInTransaction();
            return null;
        });
    }

    public void markDeadLettered(UUID jobPublicId, UUID publishedRunToken, Integer publishedAttemptCount) {
        transactions.execute(status -> {
            markDeadLetteredInTransaction(jobPublicId, publishedRunToken, publishedAttemptCount);
            return null;
        });
    }

    private JobClaim claimInTransaction(UUID jobPublicId) {
        Instant now = clock.instant();
        PracticeExplanationJob job = jobRepository.findWithLockByPublicId(jobPublicId).orElse(null);
        if (job == null || (job.getStatus() != PracticeExplanationJobStatus.QUEUED
                && job.getStatus() != PracticeExplanationJobStatus.RETRY_WAIT)
                || job.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        UUID leaseToken = UUID.randomUUID();
        job.setStatus(PracticeExplanationJobStatus.RUNNING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setLeaseToken(leaseToken);
        job.setLeaseUntil(now.plusSeconds(effectiveLeaseSeconds()));
        jobRepository.saveAndFlush(job);
        PracticeExplanation explanation = explanationRepository.findByIdAndDeletedFalse(job.getExplanationId())
                .orElse(null);
        if (explanation == null) {
            job.setStatus(PracticeExplanationJobStatus.FAILED);
            job.setLastFailureCode(PracticeConstants.PRACTICE_EXPLANATION_FAILURE_INTERNAL);
            job.setLeaseToken(null);
            job.setLeaseUntil(null);
            jobRepository.saveAndFlush(job);
            return null;
        }
        explanation.setStatus(PracticeExplanationStatus.RUNNING);
        explanation.setAttemptCount(job.getAttemptCount());
        explanation.setCanRetry(true);
        explanation.setNextAttemptAt(null);
        explanationRepository.saveAndFlush(explanation);
        return new JobClaim(job.getId(), job.getPublicId(), job.getExplanationId(), leaseToken,
                job.getAttemptCount());
    }

    private void completeInTransaction(JobClaim claim, PracticeExplanationGenerator.GeneratedExplanation generated) {
        PracticeExplanationJob job = lockedRunning(claim, false);
        if (job == null) {
            return;
        }
        PracticeExplanation explanation = explanationRepository.findByIdAndDeletedFalse(job.getExplanationId())
                .orElse(null);
        if (explanation == null) {
            return;
        }
        if (!java.util.Objects.equals(explanation.getObjectiveScore(), generated.objectiveScore())
                || !java.util.Objects.equals(explanation.getObjectiveEvaluatorVersion(), generated.objectiveEvaluatorVersion())
                || !java.util.Objects.equals(explanation.getObjectiveResultHash(), generated.objectiveResultHash())
                || !java.util.Objects.equals(explanation.getObjectiveEvidenceIdentityHash(), generated.objectiveEvidenceIdentityHash())
                || !java.util.Objects.equals(explanation.getFormWordCount(), generated.formWordCount())
                || !java.util.Objects.equals(explanation.getFormStatus(), generated.formStatus())
                || !java.util.Objects.equals(explanation.getFormEvaluatorVersion(), generated.formEvaluatorVersion())
                || !java.util.Objects.equals(explanation.getFormResultHash(), generated.formResultHash())
                || !java.util.Objects.equals(explanation.getFormEvidenceIdentityHash(), generated.formEvidenceIdentityHash())) {
            failInTransaction(claim, PracticeConstants.PRACTICE_EXPLANATION_FAILURE_INTERNAL, false);
            return;
        }
        explanation.setStatus(PracticeExplanationStatus.AVAILABLE);
        explanation.setSummary(generated.summary());
        explanation.setStrengthsJson(generated.strengthsJson());
        explanation.setImprovementsJson(generated.improvementsJson());
        explanation.setRubricFeedbackJson(generated.rubricFeedbackJson());
        explanation.setLimitationsJson(generated.limitationsJson());
        explanation.setActualModelVersion(generated.modelVersion());
        explanation.setRequestedModel(generated.providerVersion());
        explanation.setProviderVersion(PracticeExplanationGenerator.class.getName());
        explanation.setGeneratedAt(clock.instant());
        explanation.setFailureCode(null);
        explanation.setCanRetry(false);
        explanation.setNextAttemptAt(null);
        explanationRepository.saveAndFlush(explanation);
        job.setStatus(PracticeExplanationJobStatus.AVAILABLE);
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        job.setLastFailureCode(null);
        jobRepository.saveAndFlush(job);
    }

    private void failInTransaction(JobClaim claim, String failureCode, boolean allowExpiredLease) {
        PracticeExplanationJob job = lockedRunning(claim, allowExpiredLease);
        if (job == null) {
            return;
        }
        PracticeExplanation explanation = explanationRepository.findByIdAndDeletedFalse(job.getExplanationId())
                .orElse(null);
        if (explanation == null) {
            return;
        }
        Instant now = clock.instant();
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        job.setLastFailureCode(failureCode);
        explanation.setFailureCode(failureCode);
        explanation.setAttemptCount(job.getAttemptCount());
        if (job.getAttemptCount() < properties().getMaxAttempts()) {
            Instant next = now.plusSeconds(backoffSeconds(job.getAttemptCount()));
            job.setStatus(PracticeExplanationJobStatus.RETRY_WAIT);
            job.setNextAttemptAt(next);
            explanation.setStatus(PracticeExplanationStatus.RETRY_WAIT);
            explanation.setCanRetry(true);
            explanation.setNextAttemptAt(next);
            PracticeExplanationOutbox outbox = new PracticeExplanationOutbox();
            outbox.setJobId(job.getId());
            outbox.setAttempts(0);
            outbox.setNextAttemptAt(next);
            outboxRepository.save(outbox);
        } else {
            job.setStatus(PracticeExplanationJobStatus.FAILED);
            job.setNextAttemptAt(now);
            explanation.setStatus(PracticeExplanationStatus.FAILED);
            explanation.setCanRetry(false);
            explanation.setNextAttemptAt(null);
        }
        jobRepository.saveAndFlush(job);
        explanationRepository.saveAndFlush(explanation);
    }

    private void recoverExpiredInTransaction() {
        Instant now = clock.instant();
        for (PracticeExplanationJob candidate : jobRepository
                .findByStatusAndLeaseUntilLessThanEqualAndDeletedFalse(PracticeExplanationJobStatus.RUNNING, now)) {
            PracticeExplanationJob job = jobRepository.findWithLockByPublicId(candidate.getPublicId()).orElse(null);
            if (job == null || job.getStatus() != PracticeExplanationJobStatus.RUNNING
                    || job.getLeaseUntil() == null || job.getLeaseUntil().isAfter(now)) {
                continue;
            }
            failInTransaction(new JobClaim(job.getId(), job.getPublicId(), job.getExplanationId(), job.getLeaseToken(),
                    job.getAttemptCount()), PracticeConstants.PRACTICE_EXPLANATION_FAILURE_RETRYABLE, true);
        }
    }

    private void markDeadLetteredInTransaction(UUID jobPublicId, UUID publishedRunToken,
            Integer publishedAttemptCount) {
        PracticeExplanationJob job = jobRepository.findWithLockByPublicId(jobPublicId).orElse(null);
        if (job == null || publishedRunToken == null || !publishedRunToken.equals(job.getRunToken())
                || publishedAttemptCount == null || job.getAttemptCount() != publishedAttemptCount
                || (job.getStatus() != PracticeExplanationJobStatus.QUEUED
                && job.getStatus() != PracticeExplanationJobStatus.RETRY_WAIT)) {
            return;
        }
        PracticeExplanation explanation = explanationRepository.findByIdAndDeletedFalse(job.getExplanationId())
                .orElse(null);
        job.setStatus(PracticeExplanationJobStatus.FAILED);
        job.setLastFailureCode(PracticeConstants.PRACTICE_EXPLANATION_FAILURE_INTERNAL);
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        if (explanation != null) {
            explanation.setStatus(PracticeExplanationStatus.FAILED);
            explanation.setFailureCode(PracticeConstants.PRACTICE_EXPLANATION_FAILURE_INTERNAL);
            explanation.setCanRetry(false);
            explanation.setNextAttemptAt(null);
            explanationRepository.saveAndFlush(explanation);
        }
        jobRepository.saveAndFlush(job);
    }

    private PracticeExplanationJob lockedRunning(JobClaim claim, boolean allowExpiredLease) {
        PracticeExplanationJob job = jobRepository.findWithLockByPublicId(claim.jobPublicId()).orElse(null);
        if (job == null || job.getStatus() != PracticeExplanationJobStatus.RUNNING
                || !claim.leaseToken().equals(job.getLeaseToken()) || job.getLeaseUntil() == null
                || (!allowExpiredLease && !job.getLeaseUntil().isAfter(clock.instant()))) {
            return null;
        }
        return job;
    }

    private long backoffSeconds(int attempt) {
        long exponential = 1L << Math.min(Math.max(attempt - 1, 0), 10);
        return Math.min(exponential, properties().getMaxBackoffSeconds());
    }

    private long effectiveLeaseSeconds() {
        PracticeAiProperties properties = properties();
        long providerTimeoutSeconds = Math.max(1L, (properties.getTimeoutMs() + 999L) / 1_000L);
        long minimum = providerTimeoutSeconds + PracticeConstants.PRACTICE_AI_LEASE_SAFETY_SECONDS;
        long configured = Math.min(properties.getLeaseSeconds(), PracticeConstants.PRACTICE_AI_MAX_LEASE_SECONDS);
        return Math.max(configured, minimum);
    }

    private PracticeAiProperties properties() {
        PracticeAiProperties properties = propertiesProvider.getIfAvailable();
        if (properties == null) {
            PracticeAiProperties fallback = new PracticeAiProperties();
            fallback.setMaxAttempts(3);
            fallback.setLeaseSeconds(300);
            fallback.setMaxBackoffSeconds(120);
            return fallback;
        }
        return properties;
    }

    public record JobClaim(Long jobId, UUID jobPublicId, Long explanationId, UUID leaseToken, int attemptCount) {
    }
}
