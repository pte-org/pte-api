package com.pte.scoring.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row in the host's review list — summary only, no decoded payload
 * (decoding is the detail endpoint's job; a large essay/audio payload decoded
 * on every list row would be wasted work for a screen the host is just
 * scanning to pick which answer to open). {@code aiScored} says whether the
 * answer's score comes from the AI (an objective answer's does not), and
 * {@code aiFallback} marks a low-confidence AI score. {@code unscored} marks a task type the exam never
 * scores (such as Personal Introduction): its status stays {@code PENDING} but nothing is awaited.
 * {@code questionNumber} is the item's 1-based position in the exam, filled only when the list is narrowed to one
 * attempt.
 */
public record AnswerListItemResponse(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
                                      String taskType, String status, Integer rawScore, Integer teacherScore,
                                      Instant createdAt, Integer attemptNumber,
                                      boolean aiFallback, boolean aiScored, boolean unscored,
                                      Integer questionNumber) {

    public AnswerListItemResponse(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
            String taskType, String status, Integer rawScore, Integer teacherScore, Instant createdAt) {
        this(answerPublicId, attemptPublicId, sessionPublicId, taskType, status, rawScore, teacherScore,
                createdAt, null, false, false, false, null);
    }
}
