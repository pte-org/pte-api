package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One answer for the assigned examiner. {@code ai} is the AI score and its breakdown, shown with or without
 * the examiner's own score; it is only ever built after the assignment check.
 */
public record ExaminerAnswerDetailResponse(
        UUID answerPublicId,
        String taskType,
        ExaminerPromptResponse prompt,
        ExaminerAnswerPayloadResponse response,
        String status,
        Integer myScore,
        Instant submittedAt,
        AiScoreDetailResponse ai) {
}
