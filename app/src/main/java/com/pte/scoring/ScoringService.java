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
import com.pte.scoring.dto.request.FinalizeGradingCohortRequest;
import com.pte.scoring.dto.response.GradingCohortPreviewResponse;
import com.pte.scoring.dto.response.GradingCohortResponse;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.service.ObjectiveScoringService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.scoring.internal.service.ExaminerWorkQueryService;
import com.pte.scoring.internal.service.ScorePublicationLockService;
import com.pte.scoring.internal.service.ScoreSourceSelectionService;
import com.pte.scoring.internal.service.GradingCohortService;
import com.pte.scoring.internal.service.ScoredAnswerQueryService;
import com.pte.scoring.internal.service.ScoringEligibilityQueryService;
import com.pte.scoring.internal.service.ScoringReviewReadQueryService;
import com.pte.scoring.internal.service.PracticeAiScoringService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    private static final String PRACTICE_OBJECTIVE_EVALUATOR_VERSION = "objective-scoring-v1";
    private static final String PRACTICE_FORM_EVALUATOR_VERSION = "form-check-v1";

    private final ScoredAnswerQueryService scoredAnswerQueryService;
    private final ScoringEligibilityQueryService scoringEligibilityQueryService;
    private final ExaminerWorkQueryService examinerWorkQueryService;
    private final ScoringReviewReadQueryService scoringReviewReadQueryService;
    private final ScoreSourceSelectionService scoreSourceSelectionService;
    private final ScorePublicationLockService scorePublicationLockService;
    private final GradingCohortService gradingCohortService;
    private final ObjectiveScoringService objectiveScoringService;
    private final PracticeAiScoringService practiceAiScoringService;

    @Autowired
    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService,
            ScoringEligibilityQueryService scoringEligibilityQueryService,
            ExaminerWorkQueryService examinerWorkQueryService,
            ScoringReviewReadQueryService scoringReviewReadQueryService,
            ScoreSourceSelectionService scoreSourceSelectionService,
            ScorePublicationLockService scorePublicationLockService,
            GradingCohortService gradingCohortService,
            ObjectiveScoringService objectiveScoringService,
            PracticeAiScoringService practiceAiScoringService) {
        this.scoredAnswerQueryService = scoredAnswerQueryService;
        this.scoringEligibilityQueryService = scoringEligibilityQueryService;
        this.examinerWorkQueryService = examinerWorkQueryService;
        this.scoringReviewReadQueryService = scoringReviewReadQueryService;
        this.scoreSourceSelectionService = scoreSourceSelectionService;
        this.scorePublicationLockService = scorePublicationLockService;
        this.gradingCohortService = gradingCohortService;
        this.objectiveScoringService = objectiveScoringService;
        this.practiceAiScoringService = practiceAiScoringService;
    }

    /** Compatibility constructor for callers that do not use practice AI scoring. */
    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService,
            ScoringEligibilityQueryService scoringEligibilityQueryService,
            ExaminerWorkQueryService examinerWorkQueryService,
            ScoringReviewReadQueryService scoringReviewReadQueryService,
            ScoreSourceSelectionService scoreSourceSelectionService,
            ScorePublicationLockService scorePublicationLockService,
            GradingCohortService gradingCohortService,
            ObjectiveScoringService objectiveScoringService) {
        this(scoredAnswerQueryService, scoringEligibilityQueryService, examinerWorkQueryService,
                scoringReviewReadQueryService, scoreSourceSelectionService, scorePublicationLockService,
                gradingCohortService, objectiveScoringService, null);
    }

    /** Compatibility constructor for focused facade tests. */
    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService,
            ScoringEligibilityQueryService scoringEligibilityQueryService,
            ExaminerWorkQueryService examinerWorkQueryService,
            ScoringReviewReadQueryService scoringReviewReadQueryService,
            ScoreSourceSelectionService scoreSourceSelectionService,
            ScorePublicationLockService scorePublicationLockService) {
        this(scoredAnswerQueryService, scoringEligibilityQueryService, examinerWorkQueryService,
                scoringReviewReadQueryService, scoreSourceSelectionService, scorePublicationLockService, null);
    }

    /** Compatibility constructor for callers that do not use Practice objective evaluation. */
    public ScoringService(ScoredAnswerQueryService scoredAnswerQueryService,
            ScoringEligibilityQueryService scoringEligibilityQueryService,
            ExaminerWorkQueryService examinerWorkQueryService,
            ScoringReviewReadQueryService scoringReviewReadQueryService,
            ScoreSourceSelectionService scoreSourceSelectionService,
            ScorePublicationLockService scorePublicationLockService,
            GradingCohortService gradingCohortService) {
        this(scoredAnswerQueryService, scoringEligibilityQueryService, examinerWorkQueryService,
                scoringReviewReadQueryService, scoreSourceSelectionService, scorePublicationLockService,
                gradingCohortService, null);
    }

    /**
     * Scores a recorded practice response through the active task scorer. The
     * practice module persists the returned provenance; this method does not
     * create an exam attempt or mutate scoring rows.
     */
    public Optional<PracticeAiEvaluation> evaluatePracticeSpeech(String taskType,
            String audioMediaPublicId, String promptText, String referenceAnswerText, UUID tenantId) {
        if (practiceAiScoringService == null) {
            return Optional.empty();
        }
        return practiceAiScoringService.evaluateSpeech(taskType, audioMediaPublicId, promptText,
                referenceAnswerText, tenantId);
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

    /** Returns examiner assignment recipients for a cancellation notification. */
    public List<UUID> findAssignedExaminerPublicIds(UUID tenantId, UUID sessionPublicId) {
        return examinerWorkQueryService.findAssignedExaminerPublicIds(tenantId, sessionPublicId);
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

    public GradingCohortPreviewResponse previewGradingCohort(UUID sessionPublicId, UUID tenantId) {
        return gradingCohortService.preview(sessionPublicId, tenantId);
    }

    public GradingCohortResponse finalizeGradingCohort(UUID sessionPublicId, UUID tenantId, UUID actorPublicId,
            FinalizeGradingCohortRequest request) {
        return gradingCohortService.finalizeCohort(sessionPublicId, tenantId, actorPublicId, request);
    }

    public java.util.Optional<com.pte.scoring.dto.response.SessionCohortPublicationView> getCohortPublicationView(
            UUID sessionPublicId, UUID tenantId) {
        return gradingCohortService != null
                ? gradingCohortService.getCohortPublicationView(sessionPublicId, tenantId)
                : java.util.Optional.empty();
    }

    /**
     * Evaluates an objective Practice answer through the scoring module's shared
     * deterministic algorithms. AI-only task types return an empty result.
     */
    public Optional<PracticeObjectiveEvaluation> evaluatePracticeObjective(String taskType, String payload,
            String correctAnswerText, String optionsJson) {
        if (objectiveScoringService == null) {
            return Optional.empty();
        }
        try {
            TaskRuntimeProfileDescriptor runtime = TaskRuntimeProfileRegistry.descriptorFor(taskType);
            if (runtime == null || !"OBJECTIVE".equals(runtime.scoringProfileKey())) {
                return Optional.empty();
            }
            ScoringAnswer answer = new ScoringAnswer();
            answer.setTaskType(taskType);
            answer.setPayload(payload);
            answer.setCorrectAnswerText(correctAnswerText);
            answer.setOptionsJson(optionsJson);
            int rawScore = objectiveScoringService.score(answer, runtime);
            String resultHash = sha256(String.join("\n", "objective-result", PRACTICE_OBJECTIVE_EVALUATOR_VERSION,
                    nullToEmpty(taskType), nullToEmpty(payload), nullToEmpty(correctAnswerText),
                    nullToEmpty(optionsJson), String.valueOf(rawScore)));
            String evidenceIdentityHash = sha256("objective_result\n" + resultHash);
            return Optional.of(new PracticeObjectiveEvaluation(rawScore, PRACTICE_OBJECTIVE_EVALUATOR_VERSION,
                    resultHash, evidenceIdentityHash));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    /**
     * Evaluates response-form constraints without assigning a language score.
     * The result is deterministic and can be cited by the tutor as FORM_RESULT.
     */
    public Optional<PracticeFormEvaluation> evaluatePracticeForm(String taskType, String responseText,
            Integer minWordCount, Integer maxWordCount) {
        if (responseText == null) {
            return Optional.empty();
        }
        try {
            TaskRuntimeProfileDescriptor runtime = TaskRuntimeProfileRegistry.descriptorFor(taskType);
            if (runtime == null || !"AI_TEXT".equals(runtime.scoringProfileKey())) {
                return Optional.empty();
            }
            String trimmed = responseText.trim();
            int wordCount = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
            String status = minWordCount != null && wordCount < minWordCount ? "BELOW_MINIMUM"
                    : maxWordCount != null && wordCount > maxWordCount ? "ABOVE_MAXIMUM" : "IN_RANGE";
            String resultHash = sha256(String.join("\n", "form-result", PRACTICE_FORM_EVALUATOR_VERSION,
                    nullToEmpty(taskType), String.valueOf(wordCount), String.valueOf(minWordCount),
                    String.valueOf(maxWordCount), sha256(responseText)));
            String evidenceIdentityHash = sha256("form_result\n" + resultHash);
            return Optional.of(new PracticeFormEvaluation(wordCount, status, PRACTICE_FORM_EVALUATOR_VERSION,
                    resultHash, evidenceIdentityHash));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public java.util.Optional<List<String>> evaluateManualPublicationBlockers(
            UUID sessionPublicId, UUID tenantId) {
        return gradingCohortService != null
                ? gradingCohortService.evaluateManualPublicationBlockers(sessionPublicId, tenantId)
                : java.util.Optional.empty();
    }
}
