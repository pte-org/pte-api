package com.pte.scoring.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One row in the host's review list — summary only, no decoded payload
 * (decoding is the detail endpoint's job; a large essay/audio payload decoded
 * on every list row would be wasted work for a screen the host is just
 * scanning to pick which answer to open).
 */
public record AnswerListItemResponse(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
                                      String taskType, String status, Integer rawScore, Integer teacherScore,
                                      Instant createdAt, Integer attemptNumber) {

    public AnswerListItemResponse(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
            String taskType, String status, Integer rawScore, Integer teacherScore, Instant createdAt) {
        this(answerPublicId, attemptPublicId, sessionPublicId, taskType, status, rawScore, teacherScore,
                createdAt, null);
    }
}
