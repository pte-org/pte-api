package com.pte.scoring.internal.dto.response;

import com.pte.scoring.dto.response.AiScoreDetailResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Full review detail for one answer — host's primary interface for inspecting what a student answered.
 * {@code ai} is the AI side (criteria, feedback, transcript); null for an answer with none.
 */
public record AnswerReviewDetailResponse(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
                                          String taskType, String status, Integer rawScore, Integer teacherScore,
                                          Instant createdAt, DecodedAnswerPayload payload,
                                          AiScoreDetailResponse ai) {
}
