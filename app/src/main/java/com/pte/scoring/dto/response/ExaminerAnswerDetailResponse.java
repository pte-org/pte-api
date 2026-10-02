package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ExaminerAnswerDetailResponse(
        UUID answerPublicId,
        String taskType,
        ExaminerPromptResponse prompt,
        ExaminerAnswerPayloadResponse response,
        String status,
        Integer myScore,
        Instant submittedAt) {
}
