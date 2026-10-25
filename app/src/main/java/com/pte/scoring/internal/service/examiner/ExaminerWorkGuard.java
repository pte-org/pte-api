package com.pte.scoring.internal.service.examiner;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Preconditions of Examiner draft and finalize commands: the assignment row lock, the publication guard and the
 * finalize checks (cohort, membership, work state, open issues, answer manifest). Runs in the caller's transaction.
 */
class ExaminerWorkGuard {

    private final GradingAssignmentRepository assignmentRepository;
    private final GradingIssueRepository issueRepository;
    private final AiEvaluationRepository answerRepository;
    private final SessionGradingGateway gateway;
    private final AttemptService attemptService;
    private final ScoringEligibilityQueryService eligibilityQueryService;

    ExaminerWorkGuard(GradingAssignmentRepository assignmentRepository, GradingIssueRepository issueRepository,
            AiEvaluationRepository answerRepository, SessionGradingGateway gateway, AttemptService attemptService,
            ScoringEligibilityQueryService eligibilityQueryService) {
        this.assignmentRepository = assignmentRepository;
        this.issueRepository = issueRepository;
        this.answerRepository = answerRepository;
        this.gateway = gateway;
        this.attemptService = attemptService;
        this.eligibilityQueryService = eligibilityQueryService;
    }

    /** Locked work and its eligible answers, after every finalize precondition has passed in the original order. */
    record FinalizableWork(GradingAssignment work, List<AiEvaluation> eligibleAnswers) {
    }

    FinalizableWork requireFinalizable(UUID sessionPublicId, UUID attemptPublicId, GradingAssignment assignment,
            FinalizeExaminerWorkRequest request, CurrentUser user) {
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

        return new FinalizableWork(work, eligibleAnswers);
    }

    GradingAssignment lock(GradingAssignment assignment) {
        return assignmentRepository.findForUpdate(assignment.getTenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }

    void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        if (gateway.isPublished(tenantId, sessionPublicId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.SESSION_SCORES_PUBLISHED);
        }
    }
}
