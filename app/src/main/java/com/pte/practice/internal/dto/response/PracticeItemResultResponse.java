package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeScoringStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Student-safe scoring outcome of one answered item. It carries the score the exam scorers produced and
 * never the reference or correct answer. {@code retryable} is true only for a FAILED item that may still be re-submitted.
 * {@code subScoreMax} (criterion -> its maximum) is present only when {@code subScores} are Pearson trait scores of a
 * rubric version (shown as achieved / max); it is null for older results, whose sub-scores are raw 0-100 numbers.
<<<<<<< HEAD
 * {@code promptText} is the transcript of an audio-only task, released only once the item is answered.
=======
 * {@code feedbackDetail} (trait reasons, tips, word marks) is present only for a SCORED answer whose scorer wrote it.
>>>>>>> dev
 */
public record PracticeItemResultResponse(
        UUID itemPublicId,
        String taskCode,
        PracticeScoringStatus state,
        Integer score,
        String scoreStatus,
        Map<String, Integer> subScores,
        String feedback,
        String transcript,
        String failureCode,
        boolean retryable,
        Instant scoredAt,
        Map<String, Integer> subScoreMax,
<<<<<<< HEAD
        String promptText) {
=======
        PracticeAnswerFeedback feedbackDetail) {
>>>>>>> dev

    public PracticeItemResultResponse {
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
        subScoreMax = subScoreMax == null ? null : Map.copyOf(subScoreMax);
    }
}
