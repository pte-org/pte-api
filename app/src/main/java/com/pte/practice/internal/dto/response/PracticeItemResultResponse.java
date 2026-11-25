package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeScoringStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Student-safe scoring outcome of one answered item. It carries the score the exam scorers produced and
 * never the reference or correct answer. {@code retryable} is true only for a FAILED item that may still be re-submitted.
 * {@code subScoreMax} (criterion -> its maximum) is present only when {@code subScores} are Pearson trait scores of a
 * rubric version (shown as achieved / max); it is null for older results, whose sub-scores are raw 0-100 numbers.
 * {@code promptText} is the transcript of an audio-only task, released only once the item is answered.
 * {@code feedbackDetail} (trait reasons, tips, word marks) is present only for a SCORED answer whose scorer wrote it.
 * {@code optionVerdicts} says, per option of a SCORED choice or fill-in-the-blanks answer, whether the student picked
 * it and whether it is a correct one. The answer is final once scored, so the key may be shown; it is empty for every
 * other kind of answer and while an answer is not scored yet (a mock test is scored only when the student asks).
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
        String promptText,
        PracticeAnswerFeedback feedbackDetail,
        List<OptionVerdict> optionVerdicts) {

    public PracticeItemResultResponse {
        subScores = subScores == null ? Map.of() : Map.copyOf(subScores);
        subScoreMax = subScoreMax == null ? null : Map.copyOf(subScoreMax);
        optionVerdicts = optionVerdicts == null ? List.of() : List.copyOf(optionVerdicts);
    }

    /** The same result without the answer key, for a mock test that is still being sat. */
    public PracticeItemResultResponse withoutOptionVerdicts() {
        return new PracticeItemResultResponse(itemPublicId, taskCode, state, score, scoreStatus, subScores, feedback,
                transcript, failureCode, retryable, scoredAt, subScoreMax, promptText, feedbackDetail, List.of());
    }

    /**
     * One option of a scored answer: {@code orderIndex} is its position, the id the web already uses, and
     * {@code text} is what it reads. {@code blankIndex} is the gap it belongs to in a fill-in-the-blanks answer,
     * and null for a choice answer, whose options all belong to the one question.
     */
    public record OptionVerdict(int orderIndex, boolean selected, boolean correct, Integer blankIndex, String text) {

        /** An option of a choice answer, which has no gap of its own. */
        public OptionVerdict(int orderIndex, boolean selected, boolean correct, String text) {
            this(orderIndex, selected, correct, null, text);
        }
    }
}
