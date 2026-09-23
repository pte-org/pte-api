package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.dto.response.HostScoreReviewResponse;
import com.pte.scoring.dto.response.HostScoreReviewView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Host and Reporting projections are intentionally separate from Examiner work contracts. */
@Service
public class ScoringReviewReadQueryService {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ScoringMethodResolver scoringMethodResolver;

    public ScoringReviewReadQueryService(ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringMethodResolver scoringMethodResolver) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerAnswerScoreRepository = examinerAnswerScoreRepository;
        this.assignmentRepository = assignmentRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.scoringMethodResolver = scoringMethodResolver;
    }

    @Transactional(readOnly = true)
    public HostScoreReviewResponse findHostReview(UUID tenantId, UUID sessionPublicId) {
        List<ScoringAnswer> answers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantId(sessionPublicId, tenantId);
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        Map<UUID, UUID> assignedExaminers = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAttemptAssignment::getAttemptPublicId,
                        ExaminerAttemptAssignment::getExaminerPublicId));
        List<HostScoreReviewView> items = answers.stream()
                .map(answer -> toHostReview(answer, examinerScores.get(answer.getAnswerPublicId()),
                        assignedExaminers.get(answer.getAttemptPublicId())))
                .toList();

        Set<UUID> aiEligibleAttempts = answers.stream().filter(this::isAiEligible)
                .map(ScoringAnswer::getAttemptPublicId).collect(Collectors.toSet());
        int assignedAttemptCount = (int) aiEligibleAttempts.stream().filter(assignedExaminers::containsKey).count();
        int pendingExaminerCount = (int) answers.stream().filter(this::isAiEligible)
                .filter(answer -> assignedExaminers.containsKey(answer.getAttemptPublicId()))
                .filter(answer -> !isSubmitted(examinerScores.get(answer.getAnswerPublicId()))).count();
        int unavailableSelectedCount = (int) answers.stream().filter(this::isAiEligible)
                .filter(answer -> answer.getSelectedScoreSource() != null)
                .filter(answer -> selectedScore(answer, examinerScores.get(answer.getAnswerPublicId())) == null).count();
        boolean locked = sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;

        return new HostScoreReviewResponse(reviewVersion(answers, examinerScores), locked,
                aiEligibleAttempts.size(), assignedAttemptCount,
                Math.max(0, aiEligibleAttempts.size() - assignedAttemptCount), pendingExaminerCount,
                unavailableSelectedCount, items);
    }

    @Transactional(readOnly = true)
    public List<ReportScoringAnswerView> findReportInputs(UUID tenantId, UUID sessionPublicId) {
        return toReportInputs(tenantId, sessionPublicId,
                scoringAnswerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<ReportScoringAnswerView> findReportInputsForAttempt(UUID tenantId, UUID attemptPublicId) {
        List<ScoringAnswer> answers = scoringAnswerRepository.findByAttemptPublicIdAndTenantId(attemptPublicId, tenantId);
        if (answers.isEmpty()) {
            return List.of();
        }
        return toReportInputs(tenantId, answers.get(0).getSessionPublicId(), answers);
    }

    private List<ReportScoringAnswerView> toReportInputs(UUID tenantId, UUID sessionPublicId,
            List<ScoringAnswer> answers) {
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        return answers.stream().map(answer -> toReportInput(answer,
                examinerScores.get(answer.getAnswerPublicId()))).toList();
    }

    private HostScoreReviewView toHostReview(ScoringAnswer answer, ExaminerAnswerScore examinerScore,
            UUID assignedExaminerPublicId) {
        ScoringMethod method = methodFor(answer);
        boolean aiAvailable = isAiMethod(method) && isValidAi(answer);
        boolean examinerAvailable = isSubmitted(examinerScore);
        return new HostScoreReviewView(answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getTaskType(),
                scoringMethodResolver.resolveSection(answer.getScoreTemplatePublicId(), answer.getTaskType())
                        .orElse(null),
                method == null ? null : method.name(), answer.getStatus().name(), answer.getRawScore(),
                name(answer.getAiProviderCategory()), answer.getAiProvider(), answer.getAiModel(),
                answer.getAiProviderVersion(), aiAvailable,
                examinerScore == null ? null : examinerScore.getScore(),
                examinerScore == null ? "NOT_SUBMITTED" : examinerScore.getStatus().name(),
                assignedExaminerPublicId, examinerAvailable, answer.getTeacherScore(),
                name(answer.getSelectedScoreSource()), answer.getLockVersion());
    }

    private ReportScoringAnswerView toReportInput(ScoringAnswer answer, ExaminerAnswerScore examinerScore) {
        ScoringMethod method = methodFor(answer);
        String section = scoringMethodResolver.resolveSection(answer.getScoreTemplatePublicId(), answer.getTaskType())
                .orElse(null);
        Integer selectedScore = selectedScore(answer, examinerScore);
        String block = blockingReason(answer, examinerScore, method, selectedScore);
        return new ReportScoringAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getScoreTemplatePublicId(), answer.getTaskType(), section,
                method == null ? null : method.name(), answer.getRawScore(), name(answer.getAiProviderCategory()),
                answer.getAiProvider(), answer.getAiModel(), answer.getAiProviderVersion(),
                examinerScore == null ? null : examinerScore.getScore(), name(answer.getSelectedScoreSource()),
                selectedScore, block == null, block, answer.getLockVersion());
    }

    private Integer selectedScore(ScoringAnswer answer, ExaminerAnswerScore examinerScore) {
        ScoringMethod method = methodFor(answer);
        ScoreSource source = answer.getSelectedScoreSource();
        if (isAiMethod(method)) {
            if (source == ScoreSource.AI && isValidAi(answer)) {
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

    private String blockingReason(ScoringAnswer answer, ExaminerAnswerScore examinerScore, ScoringMethod method,
            Integer score) {
        if (method == null || method == ScoringMethod.UNSCORED) {
            return null;
        }
        if (method == ScoringMethod.OBJECTIVE) {
            // Preserve the existing report contract: objective answers with no score are excluded from
            // aggregation, rather than blocking publication. AI-eligible answers still require a selection.
            return null;
        }
        if (!isAiMethod(method)) {
            return ScoreReviewConstants.REASON_SCORING_METHOD_UNAVAILABLE;
        }
        if (answer.getSelectedScoreSource() == null) {
            return ScoreReviewConstants.REASON_NO_SELECTED_SOURCE;
        }
        if (answer.getSelectedScoreSource() == ScoreSource.AI && !isValidAi(answer)) {
            return ScoreReviewConstants.REASON_AI_SCORE_NOT_PUBLISHABLE;
        }
        if (answer.getSelectedScoreSource() == ScoreSource.EXAMINER && !isSubmitted(examinerScore)) {
            return ScoreReviewConstants.REASON_EXAMINER_SCORE_NOT_SUBMITTED;
        }
        return score == null ? ScoreReviewConstants.REASON_SELECTED_SCORE_UNAVAILABLE : null;
    }

    private boolean isAiEligible(ScoringAnswer answer) {
        return isAiMethod(methodFor(answer));
    }

    private boolean isValidAi(ScoringAnswer answer) {
        return answer.getStatus() == ScoringAnswerStatus.SCORED && isRangeValid(answer.getRawScore())
                && answer.getAiProviderCategory() == AiProviderCategory.REAL
                && answer.getAiProvider() != null && !answer.getAiProvider().isBlank();
    }

    private boolean isSubmitted(ExaminerAnswerScore score) {
        return score != null && score.getStatus() == ExaminerAnswerScoreStatus.SUBMITTED
                && isRangeValid(score.getScore());
    }

    private boolean isRangeValid(Integer score) {
        return score != null && score >= 0 && score <= 100;
    }

    private boolean isAiMethod(ScoringMethod method) {
        return method == ScoringMethod.AI_SPEECH || method == ScoringMethod.AI_TEXT;
    }

    private ScoringMethod methodFor(ScoringAnswer answer) {
        return scoringMethodResolver.resolve(answer.getScoreTemplatePublicId(), answer.getTaskType()).orElse(null);
    }

    public String reviewVersion(List<ScoringAnswer> answers, Map<UUID, ExaminerAnswerScore> examinerScores) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            answers.stream().sorted((left, right) -> left.getAnswerPublicId()
                    .compareTo(right.getAnswerPublicId())).forEach(answer -> {
                        ExaminerAnswerScore score = examinerScores.get(answer.getAnswerPublicId());
                        updateVersionDigest(digest, answer.getAnswerPublicId());
                        updateVersionDigest(digest, answer.getLockVersion());
                        updateVersionDigest(digest, answer.getStatus());
                        updateVersionDigest(digest, answer.getRawScore());
                        updateVersionDigest(digest, answer.getAiProviderCategory());
                        updateVersionDigest(digest, answer.getAiProvider());
                        updateVersionDigest(digest, answer.getAiModel());
                        updateVersionDigest(digest, answer.getAiProviderVersion());
                        updateVersionDigest(digest, answer.getSelectedScoreSource());
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
