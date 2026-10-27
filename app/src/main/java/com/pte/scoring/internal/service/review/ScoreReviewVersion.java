package com.pte.scoring.internal.service.review;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.service.selection.ScoreSelectionReader;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fingerprint of everything a Host score review shows, so a stale review can be detected. */
final class ScoreReviewVersion {

    private ScoreReviewVersion() {
    }

    static String of(List<AiEvaluation> answers, Map<UUID, ExaminerEvaluation> examinerScores,
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

    private static void updateVersionDigest(MessageDigest digest, Object value) {
        byte[] bytes = value == null ? null : value.toString().getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) {
            digest.update(bytes);
        }
    }
}
