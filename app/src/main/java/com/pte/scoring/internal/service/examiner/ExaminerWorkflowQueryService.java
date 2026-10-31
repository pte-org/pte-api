package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.web.PagedResult;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.dto.response.ExaminerGuidelinesResponse;
import com.pte.scoring.dto.response.ExaminerHistoryItemResponse;
import com.pte.scoring.dto.response.ExaminerHistoryResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExaminerWorkflowQueryService {

    private final ExaminerWorkRecorder recorder;
    private final GradingIssueRepository issueRepository;
    private final GradingAssignmentRepository assignmentRepository;
    private final ExaminerEvaluationRepository evaluationRepository;
    private final SessionGradingGateway gateway;
    private final AttemptService attemptService;
    private final AiEvaluationRepository answerRepository;
    private final ExamDefinitionService examDefinitionService;

    public ExaminerWorkflowQueryService(
            ExaminerWorkRecorder recorder,
            GradingIssueRepository issueRepository,
            GradingAssignmentRepository assignmentRepository,
            ExaminerEvaluationRepository evaluationRepository,
            SessionGradingGateway gateway,
            AttemptService attemptService,
            AiEvaluationRepository answerRepository,
            ExamDefinitionService examDefinitionService) {
        this.recorder = recorder;
        this.issueRepository = issueRepository;
        this.assignmentRepository = assignmentRepository;
        this.evaluationRepository = evaluationRepository;
        this.gateway = gateway;
        this.attemptService = attemptService;
        this.answerRepository = answerRepository;
        this.examDefinitionService = examDefinitionService;
    }

    public ExaminerWorkflowView buildWorkflowView(GradingAssignment assignment, UUID tenantId) {
        boolean isPublished = gateway.isPublished(tenantId, assignment.getSessionPublicId());

        Optional<SessionGradingGateway.Grading> gradingOpt = gateway.grading(tenantId, assignment.getSessionPublicId());
        boolean isCohortFrozenOrCompleted = gradingOpt.map(g ->
                (g.status() == GradingCohortStatus.FROZEN || g.status() == GradingCohortStatus.COMPLETED)
                        && g.markingMode() == GradingMarkingMode.MANUAL_EXAMINER
        ).orElse(false);

        boolean isMemberActive = true;
        if (gradingOpt.isPresent()) {
            isMemberActive = attemptService.getGradingCandidate(assignment.getAttemptPublicId(), tenantId)
                    .map(candidate -> !candidate.excluded()).orElse(false);
        }

        String blockingReason = null;
        if (isPublished) {
            blockingReason = "ALREADY_PUBLISHED";
        } else if (!isCohortFrozenOrCompleted) {
            blockingReason = "WAITING_FOR_HOST_FREEZE";
        } else if (!isMemberActive) {
            blockingReason = "EXCLUDED_FROM_COHORT";
        }

        ExaminerWorkflowStatus status;
        long workVersion = 0L;
        Instant startedAt = null;
        Instant finalizedAt = null;
        List<ExaminerDraftResponse> drafts = Collections.emptyList();

        if (assignment.isStarted()) {
            GradingAssignment work = assignment;
            status = work.getStatus();
            workVersion = work.getWorkVersion();
            startedAt = work.getStartedAt();
            finalizedAt = work.getFinalizedAt();

            List<ExaminerEvaluation> draftEntities = evaluationRepository
                    .findByTenantIdAndAssignmentIdAndDeletedFalse(tenantId, work.getId());
            drafts = draftEntities.stream()
                    .map(d -> new ExaminerDraftResponse(
                            d.getAnswerPublicId(),
                            d.getDraftVersion(),
                            d.getScore(),
                            d.getComment(),
                            d.getDecisionKind(),
                            d.getExpectedReviewVersion(),
                            d.getReviewedAiVersion(),
                            d.getSavedAt(),
                            d.isCarriedOver()))
                    .toList();
        } else {
            // Derived legacy state
            long submittedFinals = evaluationRepository.countSubmitted(
                    tenantId, assignment.getSessionPublicId(), assignment.getAttemptPublicId(), assignment.getExaminerPublicId());
            if (submittedFinals == 0) {
                status = ExaminerWorkflowStatus.PENDING;
            } else if (submittedFinals < assignment.getEligibleAnswerCount()) {
                status = ExaminerWorkflowStatus.IN_PROGRESS;
            } else {
                status = ExaminerWorkflowStatus.COMPLETED;
            }
        }

        if (blockingReason == null && drafts.stream().anyMatch(ExaminerDraftResponse::carriedOver)) {
            blockingReason = ExaminerScoringConstants.CARRIED_OVER_DRAFTS_BLOCKING_REASON;
        }

        boolean readOnly = isPublished || status == ExaminerWorkflowStatus.COMPLETED;
        boolean canStart = !readOnly && status == ExaminerWorkflowStatus.PENDING;
        boolean canSaveDraft = !readOnly && status == ExaminerWorkflowStatus.IN_PROGRESS;
        boolean canFinalize = !readOnly && status == ExaminerWorkflowStatus.IN_PROGRESS && blockingReason == null;

        return new ExaminerWorkflowView(
                "ATOMIC_DRAFT",
                status,
                workVersion,
                startedAt,
                finalizedAt,
                readOnly,
                canStart,
                canSaveDraft,
                canFinalize,
                blockingReason,
                drafts
        );
    }

    public ExaminerHistoryResponse getHistory(UUID sessionPublicId, UUID attemptPublicId, Pageable pageable, CurrentUser user) {
        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        if (!assignment.isStarted()) {
            return new ExaminerHistoryResponse(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }

        PagedResult<AuditHistoryEntry> trail = recorder.history(user.tenantId(), assignment.getPublicId(),
                pageable.getPageNumber(), pageable.getPageSize());

        List<ExaminerHistoryItemResponse> items = trail.data().stream()
                .map(row -> {
                    ExaminerWorkRecorder.Entry entry = recorder.readEntry(row);
                    var meta = entry == null ? null : entry.metadata();
                    return new ExaminerHistoryItemResponse(
                            row.publicId(),
                            assignment.getSessionPublicId(),
                            assignment.getAttemptPublicId(),
                            entry == null ? null : entry.answerPublicId(),
                            entry == null ? null : entry.operation(),
                            row.createdAt(),
                            entry == null ? 0L : entry.workVersion(),
                            entry == null ? null : entry.draftVersion(),
                            meta != null ? meta.actorPublicId() : null,
                            meta != null ? meta.actorRole() : null,
                            meta != null ? meta.assignmentPublicId() : assignment.getPublicId(),
                            meta != null ? meta.previousScore() : null,
                            meta != null ? meta.newScore() : null,
                            meta != null ? meta.rubricVersion() : null,
                            meta != null ? meta.reason() : null);
                })
                .toList();

        return new ExaminerHistoryResponse(items, trail.meta().page(), trail.meta().size(),
                trail.meta().totalElements(), trail.meta().totalPages());
    }

    public List<ExaminerGradingIssueResponse> getIssues(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        if (!assignment.isStarted()) {
            return Collections.emptyList();
        }

        List<GradingIssue> issues = issueRepository
                .findByTenantIdAndAssignmentIdAndDeletedFalseOrderByReportedAtAsc(user.tenantId(), assignment.getId());

        return issues.stream()
                .map(i -> new ExaminerGradingIssueResponse(
                        i.getPublicId(),
                        assignment.getSessionPublicId(),
                        assignment.getAttemptPublicId(),
                        i.getAnswerPublicId(),
                        i.getType(),
                        i.getDescription(),
                        i.getStatus(),
                        i.getReportedAt(),
                        i.getResolvedAt(),
                        i.getResolution()))
                .toList();
    }

    public ExaminerGuidelinesResponse getGuidelines(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        requireAssignment(sessionPublicId, attemptPublicId, user);
        List<AiEvaluation> answers = answerRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(user.tenantId(), sessionPublicId, attemptPublicId);

        List<ExaminerGuidelinesResponse.ExaminerTemplateGuidelineItem> templates = new ArrayList<>();
        List<UUID> templateIds = answers.stream()
                .map(AiEvaluation::getExamDefinitionPublicId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        for (UUID templateId : templateIds) {
            try {
                ExamDefinitionResponse template = examDefinitionService.getByPublicId(templateId);
                if (template != null) {
                    templates.add(new ExaminerGuidelinesResponse.ExaminerTemplateGuidelineItem(templateId, template.items()));
                }
            } catch (Exception ignored) {
                // Ignore missing template lookup
            }
        }

        return new ExaminerGuidelinesResponse(
                0,
                100,
                "Scalar scoring 0-100 applies to each eligible task without criterion weights.",
                templates
        );
    }

    public GradingAssignment requireAssignment(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }
}
