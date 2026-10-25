package com.pte.scoring.internal.service;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.dto.request.SaveExaminerDraftRequest;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExaminerDraftCommandService {


    private final SessionService sessionService;
    private final GradingAssignmentRepository assignmentRepository;
    private final GradingIssueRepository issueRepository;
    private final ExaminerEvaluationRepository evaluationRepository;
    private final AiEvaluationRepository answerRepository;
    private final SessionGradingGateway gateway;
    private final AttemptService attemptService;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final ExaminerWorkRecorder recorder;
    private final ExaminerFinalizeSubmitter finalizeSubmitter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ExaminerDraftCommandService(
            SessionService sessionService,
            GradingAssignmentRepository assignmentRepository,
            GradingIssueRepository issueRepository,
            ExaminerEvaluationRepository evaluationRepository,
            AiEvaluationRepository answerRepository,
            SessionGradingGateway gateway,
            AttemptService attemptService,
            ScoringEligibilityQueryService eligibilityQueryService,
            ExaminerWorkflowQueryService workflowQueryService,
            ExaminerReviewVersionService examinerReviewVersionService,
            ExaminerWorkRecorder recorder,
            ExaminerFinalizeSubmitter finalizeSubmitter,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sessionService = sessionService;
        this.assignmentRepository = assignmentRepository;
        this.issueRepository = issueRepository;
        this.evaluationRepository = evaluationRepository;
        this.answerRepository = answerRepository;
        this.gateway = gateway;
        this.attemptService = attemptService;
        this.eligibilityQueryService = eligibilityQueryService;
        this.workflowQueryService = workflowQueryService;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.recorder = recorder;
        this.finalizeSubmitter = finalizeSubmitter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ExaminerCommandResponse saveDraft(
            UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            SaveExaminerDraftRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        GradingAssignment work = lock(assignment);
        if (!work.isStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.WORK_NOT_STARTED_FOR_DRAFT);
        }

        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_FINALIZED_NO_DRAFTS);
        }
        if (request.expectedWorkVersion() != work.getWorkVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_VERSION_MISMATCH);
        }

        boolean answerBelongs = answerRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndAnswerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, answerPublicId);
        if (!answerBelongs) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ANSWER_NOT_IN_ATTEMPT);
        }

        if (request.decisionKind() == ExaminerDecisionKind.CONFIRM_AI) {
            AiEvaluation scoringAnswer = answerRepository.findByAnswerPublicId(answerPublicId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ANSWER_NOT_FOUND));
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(scoringAnswer);
            if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                    || !Objects.equals(aiRef.version(), request.reviewedAiVersion())
                    || !Objects.equals(aiRef.score(), request.score())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.CONFIRM_AI_DRAFT_MISMATCH);
            }
        }

        String fingerprint = ExaminerWorkRecorder.fingerprint(ExaminerScoringConstants.FINGERPRINT_SAVE_DRAFT + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + answerPublicId + ":" + request.requestPublicId() + ":" + request.expectedDraftVersion()
                + ":" + request.score() + ":" + (request.comment() != null ? request.comment().trim() : "")
                + ":" + request.decisionKind() + ":" + request.expectedReviewVersion() + ":" + request.reviewedAiVersion());

        Instant now = clock.instant();
        Optional<ExaminerCommandReceiptResponse> replay = recorder.replayOrBegin(user, assignment.getPublicId(),
                request.requestPublicId(), fingerprint, now);
        if (replay.isPresent()) {
            ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
            ExaminerDraftResponse draftResponse = evaluationRepository
                    .findByTenantIdAndAssignmentIdAndAnswerPublicIdAndDeletedFalse(user.tenantId(), work.getId(), answerPublicId)
                    .map(ExaminerDraftCommandService::toDraftResponse)
                    .orElse(null);
            return new ExaminerCommandResponse(replay.get(), currentView, draftResponse);
        }

        String comment = request.comment() != null ? request.comment().trim() : null;
        Optional<ExaminerEvaluation> existing = evaluationRepository
                .findByAnswerWithLock(user.tenantId(), answerPublicId);

        ExaminerEvaluation draft;
        Integer previousScore = null;
        if (existing.isEmpty()) {
            if (request.expectedDraftVersion() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.DRAFT_VERSION_MISMATCH_UNPERSISTED);
            }
            draft = evaluationRepository.save(ExaminerEvaluation.draft(user.tenantId(), sessionPublicId,
                    attemptPublicId, answerPublicId, user.userId(), work.getId(), request.score(), comment,
                    request.decisionKind(), request.expectedReviewVersion(), request.reviewedAiVersion(), now));
        } else {
            draft = existing.get();
            if (draft.isSubmitted()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.ANSWER_ALREADY_SUBMITTED);
            }
            if (request.expectedDraftVersion() != draft.getDraftVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.DRAFT_VERSION_MISMATCH_FORMAT
                        .formatted(request.expectedDraftVersion(), draft.getDraftVersion()));
            }
            previousScore = draft.getScore();
            draft.updateDraft(request.score(), comment, request.decisionKind(), request.expectedReviewVersion(),
                    request.reviewedAiVersion(), now);
            draft = evaluationRepository.save(draft);
        }

        boolean isScoreChange = previousScore != null && !Objects.equals(previousScore, request.score());
        ExaminerGradingOperation op = isScoreChange ? ExaminerGradingOperation.SCORE_CHANGE : ExaminerGradingOperation.SAVE_DRAFT;

        ExaminerGradingAuditMetadata auditMetadata = new ExaminerGradingAuditMetadata(
                user.userId(), ExaminerScoringConstants.EXAMINER_ROLE, assignment.getPublicId(), previousScore, request.score(), 1L,
                comment != null ? comment : (isScoreChange ? ExaminerScoringConstants.AUDIT_SCORE_UPDATED : ExaminerScoringConstants.AUDIT_DRAFT_SAVED));
        recorder.record(user, assignment.getPublicId(), request.requestPublicId(),
                ExaminerScoringConstants.AUDIT_DRAFT_SUMMARY_FORMAT.formatted(
                        user.userId(), op.name(), answerPublicId, request.score(), previousScore),
                new ExaminerWorkRecorder.Entry(answerPublicId, op, work.getWorkVersion(), draft.getDraftVersion(),
                        auditMetadata));

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.SAVE_DRAFT, now,
                work.getWorkVersion(), answerPublicId, draft.getDraftVersion(), Collections.emptyList());
        recorder.complete(user, assignment.getPublicId(), request.requestPublicId(), receipt);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, toDraftResponse(draft));
    }

    @Transactional
    public ExaminerCommandResponse finalizeWork(
            UUID sessionPublicId, UUID attemptPublicId, FinalizeExaminerWorkRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);

        String manifestString = request.answers().stream()
                .sorted(Comparator.comparing(FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest::answerPublicId))
                .map(a -> a.answerPublicId() + ":" + a.expectedDraftVersion())
                .collect(Collectors.joining(";"));

        String fingerprint = ExaminerWorkRecorder.fingerprint(ExaminerScoringConstants.FINGERPRINT_FINALIZE + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.expectedWorkVersion() + ":" + manifestString);

        Instant now = clock.instant();
        Optional<ExaminerCommandReceiptResponse> replay = recorder.replayOrBegin(user, assignment.getPublicId(),
                request.requestPublicId(), fingerprint, now);
        if (replay.isPresent()) {
            return new ExaminerCommandResponse(replay.get(),
                    workflowQueryService.buildWorkflowView(assignment, user.tenantId()), null);
        }

        assertNotPublished(user.tenantId(), sessionPublicId);

        SessionGradingGateway.Grading cohort = gateway.grading(user.tenantId(), sessionPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.COHORT_NOT_FOUND));

        if (cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER
                || (cohort.status() != GradingCohortStatus.FROZEN && cohort.status() != GradingCohortStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.COHORT_NOT_MANUAL_FROZEN);
        }

        AttemptGradingCandidateView member = attemptService.getGradingCandidate(attemptPublicId, user.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.ATTEMPT_NOT_IN_COHORT));
        if (member.excluded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.ATTEMPT_EXCLUDED_FROM_GRADING_COHORT);
        }

        GradingAssignment work = lock(assignment);
        if (!work.isStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.WORK_NOT_STARTED_FOR_FINALIZE);
        }

        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_ALREADY_FINALIZED);
        }
        if (request.expectedWorkVersion() != work.getWorkVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_VERSION_MISMATCH);
        }

        List<GradingIssue> openIssues = issueRepository
                .findByTenantIdAndAssignmentIdAndDeletedFalseOrderByReportedAtAsc(user.tenantId(), work.getId())
                .stream()
                .filter(i -> i.getStatus() == ExaminerGradingIssueStatus.OPEN)
                .toList();
        if (!openIssues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.BLOCKING_ISSUES_OPEN);
        }

        List<AiEvaluation> eligibleAnswers = answerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, user.tenantId(), List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();

        if (eligibleAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.ELIGIBLE_POOL_EMPTY);
        }

        Set<UUID> eligibleIds = eligibleAnswers.stream().map(AiEvaluation::getAnswerPublicId).collect(Collectors.toSet());
        Set<UUID> requestIds = request.answers().stream().map(FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest::answerPublicId).collect(Collectors.toSet());

        if (!eligibleIds.equals(requestIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.MANIFEST_POOL_MISMATCH);
        }

        List<ExaminerCommandReceiptResponse.ChildRequestReceipt> childReceipts = finalizeSubmitter.submitAll(user,
                sessionPublicId, attemptPublicId, work, request, eligibleAnswers, now);

        work.finalizeGrading(now);
        assignmentRepository.save(work);

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.FINALIZE, now,
                work.getWorkVersion(), null, null, childReceipts);
        recorder.record(user, assignment.getPublicId(), request.requestPublicId(),
                ExaminerScoringConstants.AUDIT_FINALIZED_SUMMARY_FORMAT.formatted(attemptPublicId, request.answers().size()),
                new ExaminerWorkRecorder.Entry(null, ExaminerGradingOperation.FINALIZE, work.getWorkVersion(), null,
                        new ExaminerGradingAuditMetadata(user.userId(), ExaminerScoringConstants.EXAMINER_ROLE, assignment.getPublicId(),
                                null, null, 1L, ExaminerScoringConstants.AUDIT_FINALIZED_REASON_FORMAT.formatted(request.answers().size()))));
        recorder.complete(user, assignment.getPublicId(), request.requestPublicId(), receipt);
        // The last finalized attempt completes the session's grading in this same commit, not at the next sweep.
        eventPublisher.publishEvent(new ScoringProgressChangedEvent(user.tenantId(), sessionPublicId));

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    private GradingAssignment requireAssignment(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }

    private GradingAssignment lock(GradingAssignment assignment) {
        return assignmentRepository.findForUpdate(assignment.getTenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }

    private void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        if (gateway.isPublished(tenantId, sessionPublicId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.SESSION_SCORES_PUBLISHED);
        }
    }

    private static ExaminerDraftResponse toDraftResponse(ExaminerEvaluation draft) {
        return new ExaminerDraftResponse(draft.getAnswerPublicId(), draft.getDraftVersion(), draft.getScore(),
                draft.getComment(), draft.getDecisionKind(), draft.getExpectedReviewVersion(),
                draft.getReviewedAiVersion(), draft.getSavedAt());
    }
}
