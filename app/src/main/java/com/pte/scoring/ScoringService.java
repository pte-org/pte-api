package com.pte.scoring;

import com.pte.scoring.dto.response.ScoredAnswerView;
import com.pte.scoring.dto.response.AiEligibleAttemptView;
import com.pte.scoring.dto.response.ExaminerScoringWorkItemView;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.dto.response.ScoreSourceSelectionPreviewResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionResultResponse;
import com.pte.scoring.dto.response.ScoreSourceAuditResponse;
import com.pte.scoring.dto.response.HostScoreReviewResponse;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.internal.service.ExaminerWorkQueryService;
import com.pte.scoring.internal.service.ScorePublicationLockService;
import com.pte.scoring.internal.service.ScoreSourceSelectionService;
import com.pte.scoring.internal.service.ScoredAnswerQueryService;
import com.pte.scoring.internal.service.ScoringEligibilityQueryService;
import com.pte.scoring.internal.service.ScoringReviewReadQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code scoring}. Every internal
 * service, repository, and controller stays in {@code internal/}.
 *
 * <p>Starts with the one method known to have a cross-module caller: {@code
 * reporting} (Phase 10) aggregating an attempt's scored answers into its
 * skill report. No caller existed in Phase 08 itself, so this facade wasn't
 * created until now — same YAGNI-scoped pattern as every other module's
 * public facade.
 */
@Service
public class ScoringService {

    private final ScoredAnswerQueryService scoredAnswerQueryService;
    private final ScoringEligibilityQueryService scoringEligibilityQueryService;
    private final ExaminerWorkQueryService examinerWorkQueryService;
    private final ScoringReviewReadQueryService scoringReviewReadQueryService;
    private final ScoreSourceSelectionService scoreSourceSelectionService;
    private final ScorePublicationLockService scorePublicationLockService;

    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService,
            ScoringEligibilityQueryService scoringEligibilityQueryService,
            ExaminerWorkQueryService examinerWorkQueryService,
            ScoringReviewReadQueryService scoringReviewReadQueryService,
            ScoreSourceSelectionService scoreSourceSelectionService,
            ScorePublicationLockService scorePublicationLockService) {
        this.scoredAnswerQueryService = scoredAnswerQueryService;
        this.scoringEligibilityQueryService = scoringEligibilityQueryService;
        this.examinerWorkQueryService = examinerWorkQueryService;
        this.scoringReviewReadQueryService = scoringReviewReadQueryService;
        this.scoreSourceSelectionService = scoreSourceSelectionService;
        this.scorePublicationLockService = scorePublicationLockService;
    }

    public List<ScoredAnswerView> getScoredAnswersForAttempt(UUID attemptPublicId, UUID tenantId) {
        return scoredAnswerQueryService.findScoredForAttempt(attemptPublicId, tenantId);
    }

    /** Candidate attempt counts are derived from each answer's pinned template; null filter means the whole session. */
    public List<AiEligibleAttemptView> findAiEligibleAttempts(UUID sessionPublicId, UUID tenantId,
            List<UUID> attemptPublicIds) {
        return scoringEligibilityQueryService.findEligibleAttempts(sessionPublicId, tenantId, attemptPublicIds);
    }

    /** Checks the scoring-owned session assignment; identity role/status must also be checked by the caller. */
    public boolean isAttemptAssignedToExaminer(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID examinerPublicId) {
        return examinerWorkQueryService.isAssignedToExaminer(tenantId, sessionPublicId, attemptPublicId,
                examinerPublicId);
    }

    /** Blind queue projection. The response contains no AI score, Host score, source decision, or provider metadata. */
    public List<ExaminerScoringWorkItemView> getExaminerWorkItems(UUID tenantId, UUID sessionPublicId,
            UUID examinerPublicId) {
        return examinerWorkQueryService.findWorkItems(tenantId, sessionPublicId, examinerPublicId);
    }

    /** Blind detail projection; empty means missing, cross-tenant, or not assigned to this Examiner. */
    public Optional<ExaminerScoringWorkItemView> getExaminerWorkItem(UUID tenantId, UUID examinerPublicId,
            UUID answerPublicId) {
        return examinerWorkQueryService.findWorkItem(tenantId, examinerPublicId, answerPublicId);
    }

    public HostScoreReviewResponse getHostScoreReview(UUID tenantId, UUID sessionPublicId) {
        return scoringReviewReadQueryService.findHostReview(tenantId, sessionPublicId);
    }

    /** Reporting-only read model; Host teacherScore is deliberately excluded. */
    public List<ReportScoringAnswerView> getReportScoringInputs(UUID tenantId, UUID sessionPublicId) {
        return scoringReviewReadQueryService.findReportInputs(tenantId, sessionPublicId);
    }

    public List<ReportScoringAnswerView> getReportScoringInputsForAttempt(UUID tenantId, UUID attemptPublicId) {
        return scoringReviewReadQueryService.findReportInputsForAttempt(tenantId, attemptPublicId);
    }

    public ScoreSourceSelectionPreviewResponse previewScoreSourceSelection(UUID sessionPublicId,
            SelectScoreSourceRequest request, com.pte.shared.security.CurrentUser caller) {
        return scoreSourceSelectionService.preview(sessionPublicId, request, caller);
    }

    public ScoreSourceSelectionResultResponse applyScoreSourceSelection(UUID sessionPublicId,
            SelectScoreSourceRequest request, com.pte.shared.security.CurrentUser caller) {
        return scoreSourceSelectionService.apply(sessionPublicId, request, caller);
    }

    public List<ScoreSourceAuditResponse> getScoreSourceSelectionAudits(UUID sessionPublicId,
            com.pte.shared.security.CurrentUser caller) {
        return scoreSourceSelectionService.auditHistory(sessionPublicId, caller);
    }

    /** Holds scoring row locks and installs the publication barrier until the caller transaction commits. */
    public ReportPublicationScoringView lockForReportPublication(UUID tenantId, UUID sessionPublicId,
            UUID proposedPublicationPublicId) {
        return scorePublicationLockService.lockForPublication(tenantId, sessionPublicId,
                proposedPublicationPublicId);
    }
}
