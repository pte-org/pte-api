package com.pte.scoring.internal.service;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.dto.response.HostScoreReviewResponse;
import com.pte.scoring.dto.response.HostScoreReviewView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.repository.AiDetailFlags;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Host and Reporting projections are intentionally separate from Examiner work contracts. */
@Service
public class ScoringReviewReadQueryService {

    private final AiEvaluationRepository scoringAnswerRepository;
    private final ExaminerEvaluationRepository examinerEvaluationRepository;
    private final GradingAssignmentRepository assignmentRepository;
    private final SessionGradingGateway gateway;
    private final ScoreSelectionReader selectionReader;
    private final AiScoreDetailQueryService aiDetails;
    private final AiScoringDispatcher aiScoringDispatcher;

    public ScoringReviewReadQueryService(AiEvaluationRepository scoringAnswerRepository,
            ExaminerEvaluationRepository examinerEvaluationRepository,
            GradingAssignmentRepository assignmentRepository,
            SessionGradingGateway gateway,
            ScoreSelectionReader selectionReader,
            AiScoreDetailQueryService aiDetails,
            AiScoringDispatcher aiScoringDispatcher) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerEvaluationRepository = examinerEvaluationRepository;
        this.assignmentRepository = assignmentRepository;
        this.gateway = gateway;
        this.selectionReader = selectionReader;
        this.aiDetails = aiDetails;
        this.aiScoringDispatcher = aiScoringDispatcher;
    }

    @Transactional(readOnly = true)
    public HostScoreReviewResponse findHostReview(UUID tenantId, UUID sessionPublicId) {
        List<AiEvaluation> answers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantId(sessionPublicId, tenantId);
        Map<UUID, ExaminerEvaluation> examinerScores = examinerEvaluationRepository
                .findSubmittedBySession(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));
        Map<UUID, UUID> assignedExaminers = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(GradingAssignment::getAttemptPublicId,
                        GradingAssignment::getExaminerPublicId));
        Map<UUID, ScoreSelectionReader.Selection> selections = selectionReader.forSession(sessionPublicId, tenantId);
        Map<UUID, AiDetailFlags> aiFlags = aiDetails.findFlags(tenantId,
                answers.stream().map(AiEvaluation::getAnswerPublicId).toList());
        List<HostScoreReviewView> items = answers.stream()
                .map(answer -> toHostReview(answer, examinerScores.get(answer.getAnswerPublicId()),
                        assignedExaminers.get(answer.getAttemptPublicId()), aiFlags.get(answer.getAnswerPublicId()),
                        selections.get(answer.getAnswerPublicId())))
                .toList();
        Instant staleBefore = aiScoringDispatcher.staleBefore();
        int rescorableCount = (int) answers.stream().filter(answer -> answer.isRescorable(staleBefore)).count();

        Set<UUID> aiEligibleAttempts = answers.stream().filter(this::isAiEligible)
                .map(AiEvaluation::getAttemptPublicId).collect(Collectors.toSet());
        int assignedAttemptCount = (int) aiEligibleAttempts.stream().filter(assignedExaminers::containsKey).count();
        int pendingExaminerCount = (int) answers.stream().filter(this::isAiEligible)
                .filter(answer -> assignedExaminers.containsKey(answer.getAttemptPublicId()))
                .filter(answer -> !isSubmitted(examinerScores.get(answer.getAnswerPublicId()))).count();
        int unavailableSelectedCount = (int) answers.stream().filter(this::isAiEligible)
                .filter(answer -> selections.containsKey(answer.getAnswerPublicId()))
                .filter(answer -> selectedScore(answer, examinerScores.get(answer.getAnswerPublicId()),
                        selections.get(answer.getAnswerPublicId()), null) == null).count();
        boolean locked = gateway.isPublished(tenantId, sessionPublicId);
        var cohort = gateway.grading(tenantId, sessionPublicId).orElse(null);
        String markingMode = cohort != null ? cohort.markingMode().name() : null;
        String cohortStatus = cohort != null ? cohort.status().name() : null;
        boolean canSelectScoreSource = !locked && (cohort == null || cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER);

        return new HostScoreReviewResponse(reviewVersion(answers, examinerScores, selections), locked,
                aiEligibleAttempts.size(), assignedAttemptCount,
                Math.max(0, aiEligibleAttempts.size() - assignedAttemptCount), pendingExaminerCount,
                unavailableSelectedCount, rescorableCount, items, markingMode, cohortStatus, canSelectScoreSource);
    }

    @Transactional(readOnly = true)
    public List<ReportScoringAnswerView> findReportInputs(UUID tenantId, UUID sessionPublicId) {
        return toReportInputs(tenantId, sessionPublicId,
                scoringAnswerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<ReportScoringAnswerView> findReportInputsForAttempt(UUID tenantId, UUID attemptPublicId) {
        List<AiEvaluation> answers = scoringAnswerRepository.findByAttemptPublicIdAndTenantId(attemptPublicId, tenantId);
        if (answers.isEmpty()) {
            return List.of();
        }
        return toReportInputs(tenantId, answers.get(0).getSessionPublicId(), answers);
    }

    private List<ReportScoringAnswerView> toReportInputs(UUID tenantId, UUID sessionPublicId,
            List<AiEvaluation> answers) {
        GradingMarkingMode markingMode = gateway.grading(tenantId, sessionPublicId)
                .map(SessionGradingGateway.Grading::markingMode).orElse(null);
        Map<UUID, ExaminerEvaluation> examinerScores = examinerEvaluationRepository
                .findSubmittedBySession(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));
        Map<UUID, ScoreSelectionReader.Selection> selections = selectionReader.forSession(sessionPublicId, tenantId);
        return answers.stream().map(answer -> toReportInput(answer,
                examinerScores.get(answer.getAnswerPublicId()), selections.get(answer.getAnswerPublicId()),
                markingMode)).toList();
    }

    private HostScoreReviewView toHostReview(AiEvaluation answer, ExaminerEvaluation examinerScore,
            UUID assignedExaminerPublicId, AiDetailFlags aiFlags, ScoreSelectionReader.Selection selection) {
        ScoringMethod method = answer.getScoringMethod();
        boolean aiAvailable = isAiScored(method) && answer.hasPublishableAiScore();
        boolean examinerAvailable = isSubmitted(examinerScore);
        return new HostScoreReviewView(answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getTaskType(),
                answer.getSection(),
                method == null ? null : method.name(), answer.getStatus().name(), answer.getRawScore(),
                name(answer.getAiProviderCategory()), answer.getAiProvider(), answer.getAiModel(),
                answer.getAiProviderVersion(), aiAvailable,
                examinerScore == null ? null : examinerScore.getScore(),
                examinerScore == null ? "NOT_SUBMITTED" : examinerScore.getStatus().name(),
                assignedExaminerPublicId, examinerAvailable, answer.getTeacherScore(),
                selection == null ? null : selection.source().name(), answer.getLockVersion(),
                aiFlags != null && aiFlags.fallback(),
                // The reason belongs to the last failed run; it is stale once the answer is queued again.
                aiFlags != null && answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED
                        ? name(aiFlags.failureReason()) : null);
    }

    private ReportScoringAnswerView toReportInput(AiEvaluation answer, ExaminerEvaluation examinerScore,
            ScoreSelectionReader.Selection selection, GradingMarkingMode markingMode) {
        ScoringMethod method = answer.getScoringMethod();
        String section = answer.getSection();
        Integer selectedScore = selectedScore(answer, examinerScore, selection, markingMode);
        String block = blockingReason(answer, examinerScore, selection, method, selectedScore, markingMode);
        return new ReportScoringAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getExamDefinitionPublicId(), answer.getTaskType(), section,
                method == null ? null : method.name(), answer.getRawScore(), name(answer.getAiProviderCategory()),
                answer.getAiProvider(), answer.getAiModel(), answer.getAiProviderVersion(),
                examinerScore == null ? null : examinerScore.getScore(),
                selection == null ? null : selection.source().name(),
                selectedScore, block == null, block, answer.getLockVersion(),
                provisionalScore(answer, method, selection, selectedScore));
    }

    /** Until a source is chosen, an AI-scored answer's own AI score stands in; a chosen source always wins. */
    private Integer provisionalScore(AiEvaluation answer, ScoringMethod method, ScoreSelectionReader.Selection selection,
            Integer selectedScore) {
        if (selectedScore == null && isAiScored(method) && selection == null
                && answer.hasPublishableAiScore()) {
            return answer.getRawScore();
        }
        return selectedScore;
    }

    private Integer selectedScore(AiEvaluation answer, ExaminerEvaluation examinerScore,
            ScoreSelectionReader.Selection selection, GradingMarkingMode markingMode) {
        ScoringMethod method = answer.getScoringMethod();
        ScoreSource source = selection == null ? null : selection.source();
        if (isAiScored(method)) {
            if (markingMode == GradingMarkingMode.MANUAL_EXAMINER) {
                if (source == ScoreSource.EXAMINER && isSubmitted(examinerScore)) {
                    return examinerScore.getScore();
                }
                return null;
            }
            if (source == ScoreSource.AI && answer.hasPublishableAiScore()) {
                return answer.getRawScore();
            }
            if (source == ScoreSource.EXAMINER && isSubmitted(examinerScore)) {
                return examinerScore.getScore();
            }
        }
        if (method == ScoringMethod.OBJECTIVE && answer.getStatus() == ScoringAnswerStatus.SCORED
                && isRangeValid(answer.getRawScore())) {
            return answer.getRawScore();
        }
        return null;
    }

    private String blockingReason(AiEvaluation answer, ExaminerEvaluation examinerScore,
            ScoreSelectionReader.Selection selection, ScoringMethod method, Integer score,
            GradingMarkingMode markingMode) {
        ScoreSource source = selection == null ? null : selection.source();
        if (method == null || method == ScoringMethod.UNSCORED) {
            return null;
        }
        if (method == ScoringMethod.OBJECTIVE) {
            // Preserve the existing report contract: objective answers with no score are excluded from
            // aggregation, rather than blocking publication. AI-eligible answers still require a selection.
            return null;
        }
        if (!isAiScored(method)) {
            return ScoreReviewConstants.REASON_SCORING_METHOD_UNAVAILABLE;
        }
        if (markingMode == GradingMarkingMode.MANUAL_EXAMINER) {
            if (source != ScoreSource.EXAMINER) {
                return ScoreReviewConstants.REASON_NO_SELECTED_SOURCE;
            }
            if (!isSubmitted(examinerScore)) {
                return ScoreReviewConstants.REASON_EXAMINER_SCORE_NOT_SUBMITTED;
            }
            if (examinerScore.getPublicId() != null && selection.evaluationPublicId() != null
                    && !Objects.equals(examinerScore.getPublicId(), selection.evaluationPublicId())) {
                return ScoreReviewConstants.REASON_SELECTED_SCORE_UNAVAILABLE;
            }
            return score == null ? ScoreReviewConstants.REASON_SELECTED_SCORE_UNAVAILABLE : null;
        }
        if (source == null) {
            return ScoreReviewConstants.REASON_NO_SELECTED_SOURCE;
        }
        if (source == ScoreSource.AI && !answer.hasPublishableAiScore()) {
            return ScoreReviewConstants.REASON_AI_SCORE_NOT_PUBLISHABLE;
        }
        if (source == ScoreSource.EXAMINER && !isSubmitted(examinerScore)) {
            return ScoreReviewConstants.REASON_EXAMINER_SCORE_NOT_SUBMITTED;
        }
        return score == null ? ScoreReviewConstants.REASON_SELECTED_SCORE_UNAVAILABLE : null;
    }

    private boolean isAiEligible(AiEvaluation answer) {
        return isAiScored(answer.getScoringMethod());
    }

    private boolean isSubmitted(ExaminerEvaluation score) {
        return score != null && score.isPublishable();
    }

    private boolean isRangeValid(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }

    private boolean isAiScored(ScoringMethod method) {
        return method != null && method.isAiScored();
    }

    public String reviewVersion(List<AiEvaluation> answers, Map<UUID, ExaminerEvaluation> examinerScores,
            Map<UUID, ScoreSelectionReader.Selection> selections) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            answers.stream().sorted((left, right) -> left.getAnswerPublicId()
                    .compareTo(right.getAnswerPublicId())).forEach(answer -> {
                        ExaminerEvaluation score = examinerScores.get(answer.getAnswerPublicId());
                        updateVersionDigest(digest, answer.getAnswerPublicId());
                        updateVersionDigest(digest, answer.getLockVersion());
                        updateVersionDigest(digest, answer.getStatus());
                        updateVersionDigest(digest, answer.getRawScore());
                        updateVersionDigest(digest, answer.getAiProviderCategory());
                        updateVersionDigest(digest, answer.getAiProvider());
                        updateVersionDigest(digest, answer.getAiModel());
                        updateVersionDigest(digest, answer.getAiProviderVersion());
                        updateVersionDigest(digest, ScoreSelectionReader.sourceOf(selections, answer.getAnswerPublicId()));
                        updateVersionDigest(digest, score == null ? null : score.getPublicId());
                        updateVersionDigest(digest, score == null ? null : score.getLockVersion());
                        updateVersionDigest(digest, score == null ? null : score.getScore());
                        updateVersionDigest(digest, score == null ? null : score.getStatus());
                        updateVersionDigest(digest, score == null ? null : score.getSubmittedAt());
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ScoringConstants.REVIEW_VERSION_HASH_UNAVAILABLE, ex);
        }
    }

    private void updateVersionDigest(MessageDigest digest, Object value) {
        byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) {
            digest.update(bytes);
        }
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
