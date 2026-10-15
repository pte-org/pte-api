package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
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

    public ExaminerAiAvailability determineAiAvailability(ScoringAnswer answer) {
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

    public String computeAiVersion(ScoringAnswer answer) {
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

        return sha256Hex(encodeFields(availability.name(), scoreStr, provider, model, providerVersion, scoredAt));
    }

    public ExaminerAiReferenceResponse buildAiReference(ScoringAnswer answer) {
        if (answer == null) {
            return new ExaminerAiReferenceResponse(ExaminerAiAvailability.UNAVAILABLE, null, null,
                    computeAiVersion(null));
        }
        ExaminerAiAvailability availability = determineAiAvailability(answer);
        Integer score = availability == ExaminerAiAvailability.AVAILABLE ? answer.getRawScore() : null;
        java.time.Instant scoredAt = availability == ExaminerAiAvailability.AVAILABLE ? answer.getScoredAt() : null;
        String version = computeAiVersion(answer);
        return new ExaminerAiReferenceResponse(availability, score, scoredAt, version);
    }

    public String computeReviewVersion(ScoringAnswer answer, ExaminerAttemptAssignment assignment,
            GradingCohort cohort, GradingCohortMember member, ExaminerAnswerScore savedScore,
            ScoringSessionState sessionState) {
        ExaminerAiReferenceResponse aiRef = buildAiReference(answer);

        String answerId = answer != null && answer.getAnswerPublicId() != null
                ? answer.getAnswerPublicId().toString() : "";
        String pinnedItemId = answer != null && answer.getPinnedItemPublicId() != null
                ? answer.getPinnedItemPublicId().toString() : "";
        String taskType = answer != null && answer.getTaskType() != null ? answer.getTaskType() : "";
        String payload = answer != null && answer.getPayload() != null ? answer.getPayload() : "";
        String correctAnswer = answer != null && answer.getCorrectAnswerText() != null
                ? answer.getCorrectAnswerText() : "";
        String optionsJson = answer != null && answer.getOptionsJson() != null ? answer.getOptionsJson() : "";

        String examinerId = assignment != null && assignment.getExaminerPublicId() != null
                ? assignment.getExaminerPublicId().toString() : "";

        String cohortId = cohort != null && cohort.getPublicId() != null ? cohort.getPublicId().toString() : "";
        String markingMode = cohort != null && cohort.getMarkingMode() != null ? cohort.getMarkingMode().name() : "";
        String cohortStatus = cohort != null && cohort.getStatus() != null ? cohort.getStatus().name() : "";
        String cohortVersion = cohort != null ? String.valueOf(cohort.getCohortVersion()) : "";

        String memberExcluded = member != null ? String.valueOf(member.isExcluded()) : "";
        String dispositionReason = member != null && member.getDispositionReason() != null
                ? member.getDispositionReason() : "";

        String aiAvailability = aiRef.availability().name();
        String aiVersion = aiRef.version();

        String decisionStatus = savedScore != null && savedScore.getStatus() != null
                ? savedScore.getStatus().name() : "PENDING";
        String decisionScore = savedScore != null ? String.valueOf(savedScore.getScore()) : "";
        String decisionKind = savedScore != null && savedScore.getDecisionKind() != null
                ? savedScore.getDecisionKind().name() : "";
        String finalizedAt = savedScore != null && savedScore.getSubmittedAt() != null
                ? savedScore.getSubmittedAt().toString() : "";

        String pubId = sessionState != null && sessionState.getPublicationPublicId() != null
                ? sessionState.getPublicationPublicId().toString() : "";
        String pubLockedAt = sessionState != null && sessionState.getPublicationLockedAt() != null
                ? sessionState.getPublicationLockedAt().toString() : "";

        return sha256Hex(encodeFields(
                answerId, pinnedItemId, taskType, payload, correctAnswer, optionsJson,
                examinerId, cohortId, markingMode, cohortStatus, cohortVersion,
                memberExcluded, dispositionReason, aiAvailability, aiVersion,
                decisionStatus, decisionScore, decisionKind, finalizedAt,
                pubId, pubLockedAt));
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

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
