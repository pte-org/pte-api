package com.pte.scoring.internal.service.examiner;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.ScoringDomainConstants;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerDecisionResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.exception.OldPayloadUnsupportedException;
import com.pte.scoring.internal.exception.examiner.ConfirmAiMismatchException;
import com.pte.scoring.internal.exception.examiner.ExaminerCohortRequiredException;
import com.pte.scoring.internal.exception.examiner.ExaminerReceiptConflictException;
import com.pte.scoring.internal.exception.examiner.ExaminerScoreConflictException;
import com.pte.scoring.internal.exception.examiner.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.examiner.InvalidDecisionKindException;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerScoreException;
import com.pte.scoring.internal.exception.examiner.StaleReviewVersionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable single-answer Examiner score submission. Runs inside the caller's write transaction, so the
 * session, answer and publication locks are taken in the same order as before the split.
 */
class ExaminerDirectScoreSubmitter {

    private static final String EXAMINER_SOURCE = "EXAMINER";

    private final GradingAssignmentRepository assignmentRepository;
    private final AiEvaluationRepository scoringAnswerRepository;
    private final ExaminerEvaluationRepository examinerEvaluationRepository;
    private final SessionGradingGateway gateway;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerContentReader contentReader;
    private final AttemptService attemptService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final SessionService sessionService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final Clock clock;

    ExaminerDirectScoreSubmitter(GradingAssignmentRepository assignmentRepository,
            AiEvaluationRepository scoringAnswerRepository,
            ExaminerEvaluationRepository examinerEvaluationRepository, SessionGradingGateway gateway,
            ScoringEligibilityQueryService eligibilityQueryService, AnswerContentReader contentReader,
            AttemptService attemptService, EntityManager entityManager, ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService, SessionService sessionService,
            ExaminerWorkflowQueryService workflowQueryService, Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerEvaluationRepository = examinerEvaluationRepository;
        this.gateway = gateway;
        this.eligibilityQueryService = eligibilityQueryService;
        this.contentReader = contentReader;
        this.attemptService = attemptService;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.sessionService = sessionService;
        this.workflowQueryService = workflowQueryService;
        this.clock = clock;
    }

    ExaminerScoreSubmissionResponse submit(UUID answerPublicId, SubmitExaminerScoreRequest request,
            CurrentUser examiner) {
        if (request == null || request.score() == null || request.score() < 0 || request.score() > 100) {
            throw new InvalidExaminerScoreException();
        }
        AiEvaluation answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(ExaminerWorkNotFoundException::new);
        if (!answer.getTenantId().equals(examiner.tenantId())
                || !assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                        examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(),
                        examiner.userId())
                || !eligibilityQueryService.isAiEligible(answer)) {
            throw new ExaminerWorkNotFoundException();
        }

        if (sessionService != null) {
            sessionService.lockForScoreReviewMutation(answer.getSessionPublicId(), examiner.tenantId());
        }

        entityManager.lock(answer, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(answer, LockModeType.PESSIMISTIC_WRITE);
        lockSessionPublicationIfPresent(answer.getTenantId(), answer.getSessionPublicId());

        GradingAssignment assignment = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndExaminerPublicId(
                        examiner.tenantId(), answer.getSessionPublicId(), examiner.userId())
                .stream()
                .filter(a -> a.getAttemptPublicId().equals(answer.getAttemptPublicId()))
                .findFirst().orElse(null);

        if (assignment != null && workflowQueryService != null) {
            ExaminerWorkflowView workflow = workflowQueryService.buildWorkflowView(assignment, examiner.tenantId());
            if (workflow != null && "ATOMIC_DRAFT".equals(workflow.mode())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Single-item score submission is disabled for ATOMIC_DRAFT workflow. Use draft and atomic finalize.");
            }
        }

        SessionGradingGateway.Grading cohort = gateway.grading(examiner.tenantId(), answer.getSessionPublicId())
                .orElse(null);
        AttemptGradingCandidateView member = cohort == null ? null
                : attemptService.getGradingCandidate(answer.getAttemptPublicId(), examiner.tenantId()).orElse(null);
        SessionGradingGateway.Publication sessionState = gateway
                .publication(examiner.tenantId(), answer.getSessionPublicId()).orElse(null);

        if (cohort != null) {
            if (cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.INCOMPATIBLE_COHORT_MODE);
            }
            if (cohort.status() != GradingCohortStatus.FROZEN && cohort.status() != GradingCohortStatus.COMPLETED) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.COHORT_NOT_FROZEN);
            }
            if (member == null || member.excluded()) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.EXCLUDED_COHORT_MEMBER);
            }
            if (request.decisionKind() == null || request.requestPublicId() == null || request.expectedReviewVersion() == null) {
                throw new OldPayloadUnsupportedException();
            }
            if (request.decisionKind() == ExaminerDecisionKind.LEGACY_IMPORTED) {
                throw new InvalidDecisionKindException();
            }
        } else if (request.decisionKind() != null) {
            throw new ExaminerCohortRequiredException(ExaminerScoringConstants.MISSING_MANUAL_COHORT);
        }

        if (request.decisionKind() == ExaminerDecisionKind.LEGACY_IMPORTED) {
            throw new InvalidDecisionKindException();
        }

        // Check cross-answer request key reuse
        if (request.requestPublicId() != null) {
            Optional<ExaminerEvaluation> existingWithKey = examinerEvaluationRepository
                    .findSubmittedByRequest(
                            examiner.tenantId(), examiner.userId(), request.requestPublicId());
            if (existingWithKey.isPresent() && !existingWithKey.get().getAnswerPublicId().equals(answerPublicId)) {
                throw new ExaminerReceiptConflictException();
            }
        }

        // Idempotent retry lookup before current AI validation or freshness check
        ExaminerEvaluation row = examinerEvaluationRepository
                .findByAnswerPublicIdAndTenantId(answerPublicId, examiner.tenantId()).orElse(null);
        ExaminerEvaluation existing = row != null && row.isSubmitted() ? row : null;
        SubmittedAnswerView content = contentReader.one(answerPublicId, examiner.tenantId()).orElse(null);
        if (existing != null) {
            String retryFingerprint = null;
            if (request.decisionKind() != null) {
                retryFingerprint = examinerReviewVersionService.computeRequestFingerprint(
                        answerPublicId, request.score(), request.decisionKind(),
                        request.expectedReviewVersion(), request.reviewedAiVersion());
            }
            try {
                existing.requireIdenticalRetry(examiner.userId(), request.score(),
                        request.requestPublicId(), retryFingerprint);
            } catch (IllegalStateException ex) {
                if (ScoringDomainConstants.EXAMINER_RECEIPT_CONFLICT.equals(ex.getMessage())) {
                    throw new ExaminerReceiptConflictException();
                }
                throw new ExaminerScoreConflictException();
            }
            String currentReviewVersion = examinerReviewVersionService.computeReviewVersion(
                    answer, content, assignment, cohort, member, existing, sessionState);
            return toSubmission(existing, currentReviewVersion);
        }

        // New decision: validate AI reference and review version freshness
        Integer reviewedAiScore = null;
        String reviewedAiVersion = request.reviewedAiVersion();
        if (request.decisionKind() == ExaminerDecisionKind.CONFIRM_AI) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                    || !Objects.equals(aiRef.version(), request.reviewedAiVersion())
                    || !Objects.equals(aiRef.score(), request.score())) {
                throw new ConfirmAiMismatchException();
            }
            reviewedAiScore = aiRef.score();
            reviewedAiVersion = aiRef.version();
        } else if (request.decisionKind() == ExaminerDecisionKind.MANUAL) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() == ExaminerAiAvailability.AVAILABLE) {
                if (request.reviewedAiVersion() != null && !Objects.equals(aiRef.version(), request.reviewedAiVersion())) {
                    throw new StaleReviewVersionException();
                }
                reviewedAiScore = aiRef.score();
                reviewedAiVersion = aiRef.version();
            } else {
                reviewedAiScore = null;
                reviewedAiVersion = null;
            }
        }

        if (request.decisionKind() != null) {
            String currentReviewVersion = examinerReviewVersionService.computeReviewVersion(
                    answer, content, assignment, cohort, member, null, sessionState);
            if (!Objects.equals(currentReviewVersion, request.expectedReviewVersion())) {
                throw new StaleReviewVersionException();
            }
        }

        String fingerprint = null;
        if (request.decisionKind() != null) {
            fingerprint = examinerReviewVersionService.computeRequestFingerprint(
                    answerPublicId, request.score(), request.decisionKind(),
                    request.expectedReviewVersion(), request.reviewedAiVersion());
        }

        Instant submittedAt = clock.instant();
        ExaminerEvaluation toSave;
        if (row != null) {
            row.submit(request.score(), submittedAt, request.decisionKind(), reviewedAiScore, reviewedAiVersion,
                    request.expectedReviewVersion(), request.requestPublicId(), fingerprint);
            toSave = row;
        } else {
            toSave = ExaminerEvaluation.submittedDirectly(answer.getTenantId(), answer.getSessionPublicId(),
                    answer.getAttemptPublicId(), answer.getAnswerPublicId(), examiner.userId(), request.score(),
                    submittedAt, request.decisionKind(), reviewedAiScore, reviewedAiVersion,
                    request.expectedReviewVersion(), request.requestPublicId(), fingerprint);
        }

        ExaminerEvaluation saved;
        try {
            saved = examinerEvaluationRepository.saveAndFlush(toSave);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ExaminerReceiptConflictException();
        }

        if (request.decisionKind() != null) {
            attemptService.selectScoreSource(List.of(answer.getAnswerPublicId()), answer.getTenantId(),
                    EXAMINER_SOURCE, examiner.userId(), saved.getSubmittedAt(),
                    Map.of(answer.getAnswerPublicId(), saved.getPublicId()));
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.pte.scoring.ScoringProgressChangedEvent(
                    answer.getTenantId(), answer.getSessionPublicId()));
        }
        String updatedReviewVersion = examinerReviewVersionService.computeReviewVersion(
                answer, content, assignment, cohort, member, saved, sessionState);
        return toSubmission(saved, updatedReviewVersion);
    }

    /** The session row is already locked by the caller, so publication can only be read, not raced. */
    private void lockSessionPublicationIfPresent(UUID tenantId, UUID sessionPublicId) {
        if (gateway.isPublished(tenantId, sessionPublicId)) {
            throw new ExaminerScoreConflictException();
        }
    }

    private ExaminerScoreSubmissionResponse toSubmission(ExaminerEvaluation score, String reviewVersion) {
        ExaminerDecisionResponse decision = new ExaminerDecisionResponse(
                score.getStatus().name(), score.getScore(), score.getDecisionKind(),
                score.getSubmittedAt(), score.getReviewedAiScore(), score.getReviewedAiVersion(),
                score.getRequestPublicId());
        return new ExaminerScoreSubmissionResponse(score.getAnswerPublicId(), score.getScore(),
                score.getStatus().name(), score.getSubmittedAt(), decision, "EXAMINER", reviewVersion);
    }
}
