package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Computes deterministic, canonical length-prefixed SHA-256 hashes for:
 * 1. AI reference versions (excluding secrets/PII)
 * 2. Per-answer review versions (excluding temporary signed URLs)
 * 3. Request receipts and fingerprints (for idempotent replays)
 */
@Service
public class ExaminerReviewVersionService {
    private final tools.jackson.databind.json.JsonMapper json;
    private final com.pte.scoring.RubricScaleService scales;

    @org.springframework.beans.factory.annotation.Autowired
    public ExaminerReviewVersionService(tools.jackson.databind.json.JsonMapper json, com.pte.scoring.RubricScaleService scales) {
        this.json = json; this.scales = scales;
    }

    public ExaminerReviewVersionService() { this(tools.jackson.databind.json.JsonMapper.builder().build(), null); }


    private ExaminerAiAvailability determineAiAvailability(AiEvaluation answer) {
        if (answer == null) {
            return ExaminerAiAvailability.UNAVAILABLE;
        }
        if (answer.hasPublishableAiScore()) {
            return ExaminerAiAvailability.AVAILABLE;
        }
        if (answer.getStatus() == ScoringAnswerStatus.PENDING
                || answer.getStatus() == ScoringAnswerStatus.AI_SCORING) {
            return ExaminerAiAvailability.PENDING;
        }
        if (answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED) {
            return ExaminerAiAvailability.FAILED;
        }
        return ExaminerAiAvailability.UNAVAILABLE;
    }

    private String computeAiVersion(AiEvaluation answer) {
        if (answer == null) {
            return sha256Hex(encodeFields("UNAVAILABLE", "", "", "", "", ""));
        }
        ExaminerAiAvailability availability = determineAiAvailability(answer);
        String scoreStr = availability == ExaminerAiAvailability.AVAILABLE && answer.getRawScore() != null
                ? String.valueOf(answer.getRawScore()) : "";
        String provider = availability == ExaminerAiAvailability.AVAILABLE && answer.getAiProvider() != null
                ? answer.getAiProvider() : "";
        String model = availability == ExaminerAiAvailability.AVAILABLE && answer.getAiModel() != null
                ? answer.getAiModel() : "";
        String providerVersion = availability == ExaminerAiAvailability.AVAILABLE && answer.getAiProviderVersion() != null
                ? answer.getAiProviderVersion() : "";
        String scoredAt = availability == ExaminerAiAvailability.AVAILABLE && answer.getScoredAt() != null
                ? answer.getScoredAt().toString() : "";

        return sha256Hex(encodeFields(availability.name(), scoreStr, provider, model, providerVersion, scoredAt, answer.getCriteriaScoresJson(),
                String.valueOf(answer.getRubricVersionPublicId())));
    }

    public ExaminerAiReferenceResponse buildAiReference(AiEvaluation answer) {
        if (answer == null) {
            return new ExaminerAiReferenceResponse(ExaminerAiAvailability.UNAVAILABLE, null, null,
                    computeAiVersion(null));
        }
        ExaminerAiAvailability availability = determineAiAvailability(answer);
        Integer score = availability == ExaminerAiAvailability.AVAILABLE ? answer.getRawScore() : null;
        java.time.Instant scoredAt = availability == ExaminerAiAvailability.AVAILABLE ? answer.getScoredAt() : null;
        String version = computeAiVersion(answer);
        var traits = com.pte.scoring.CriteriaScores.read(json, answer.getCriteriaScoresJson());
        return new ExaminerAiReferenceResponse(availability, score, scoredAt, version,
                availability == ExaminerAiAvailability.AVAILABLE ? traits.traits() : java.util.Map.of(), traits.rubricVersion(),
                scales == null ? java.util.Map.of() : scales.traitMaxima(traits.rubricVersion(), answer.getTaskType()).orElse(java.util.Map.of()));
    }

    public String computeReviewVersion(AiEvaluation answer, SubmittedAnswerView content,
            GradingAssignment assignment, SessionGradingGateway.Grading cohort, AttemptGradingCandidateView member,
            ExaminerEvaluation savedScore,
            SessionGradingGateway.Publication sessionState) {
        ExaminerAiReferenceResponse aiRef = buildAiReference(answer);

        String answerId = answer != null && answer.getAnswerPublicId() != null
                ? answer.getAnswerPublicId().toString() : "";
        String pinnedItemId = answer != null && answer.getItemPublicId() != null
                ? answer.getItemPublicId().toString() : "";
        String taskType = answer != null && answer.getTaskType() != null ? answer.getTaskType() : "";
        String payload = content != null && content.payload() != null ? content.payload() : "";
        String correctAnswer = content != null && content.correctAnswerText() != null
                ? content.correctAnswerText() : "";
        String optionsJson = content != null && content.optionsJson() != null ? content.optionsJson() : "";

        String examinerId = assignment != null && assignment.getExaminerPublicId() != null
                ? assignment.getExaminerPublicId().toString() : "";

        String cohortId = cohort != null ? cohort.sessionPublicId().toString() : "";
        String markingMode = cohort != null && cohort.markingMode() != null ? cohort.markingMode().name() : "";
        String cohortStatus = cohort != null && cohort.status() != null ? cohort.status().name() : "";
        String cohortVersion = cohort != null ? "1" : "";

        String memberExcluded = member != null ? String.valueOf(member.excluded()) : "";
        String dispositionReason = member != null && member.exclusionReason() != null
                ? member.exclusionReason() : "";

        String aiAvailability = aiRef.availability().name();
        String aiVersion = aiRef.version();

        String decisionStatus = savedScore != null && savedScore.getStatus() != null
                ? savedScore.getStatus().name() : "PENDING";
        String decisionScore = savedScore != null ? String.valueOf(savedScore.getScore()) : "";
        String decisionKind = savedScore != null && savedScore.getDecisionKind() != null
                ? savedScore.getDecisionKind().name() : "";
        String finalizedAt = savedScore != null && savedScore.getSubmittedAt() != null
                ? savedScore.getSubmittedAt().toString() : "";

        String pubId = sessionState != null && sessionState.publicationPublicId() != null
                ? sessionState.publicationPublicId().toString() : "";
        String pubLockedAt = sessionState != null && sessionState.lockedAt() != null
                ? sessionState.lockedAt().toString() : "";

        return sha256Hex(encodeFields(
                answerId, pinnedItemId, taskType, payload, correctAnswer, optionsJson,
                examinerId, cohortId, markingMode, cohortStatus, cohortVersion,
                memberExcluded, dispositionReason, aiAvailability, aiVersion,
                decisionStatus, decisionScore, decisionKind, finalizedAt,
                pubId, pubLockedAt, savedScore == null ? null : savedScore.getTraitScoresJson(),
                savedScore == null ? null : String.valueOf(savedScore.getRubricVersionPublicId())));
    }

    public String computeRequestFingerprint(UUID answerPublicId, int score, ExaminerDecisionKind kind,
            String expectedReviewVersion, String reviewedAiVersion) {
        String answerStr = answerPublicId != null ? answerPublicId.toString() : "";
        String scoreStr = String.valueOf(score);
        String kindStr = kind != null ? kind.name() : "";
        String expectedVersionStr = expectedReviewVersion != null ? expectedReviewVersion : "";
        String reviewedAiVersionStr = reviewedAiVersion != null ? reviewedAiVersion : "";

        return sha256Hex(encodeFields(answerStr, scoreStr, kindStr, expectedVersionStr, reviewedAiVersionStr));
    }

    public String computeRequestFingerprint(UUID answer, Integer score, ExaminerDecisionKind kind,
            String review, String ai, java.util.Map<String, java.math.BigDecimal> traits, UUID rubric) {
        if (traits == null && rubric == null && score != null) return computeRequestFingerprint(answer, score.intValue(), kind, review, ai);
        var canonical = com.pte.scoring.internal.service.examiner.trait.ExaminerTraitInputs.integers(traits);
        return sha256Hex(encodeFields(String.valueOf(answer), String.valueOf(score), String.valueOf(kind),
                review, ai, canonical == null ? null : canonicalTraits(canonical), String.valueOf(rubric)));
    }

    private static String canonicalTraits(java.util.Map<String, Integer> traits) {
        StringBuilder encoded = new StringBuilder();
        new java.util.TreeMap<>(traits).forEach((key, value) -> encoded.append(encodeFields(key, value == null ? null : value.toString())));
        return encoded.toString();
    }

    private static String encodeFields(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            if (field == null) {
                sb.append("-1:|");
            } else {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                sb.append(bytes.length).append(':').append(field).append('|');
            }
        }
        return sb.toString();
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ExaminerScoringConstants.SHA_256_UNAVAILABLE, e);
        }
    }
}
