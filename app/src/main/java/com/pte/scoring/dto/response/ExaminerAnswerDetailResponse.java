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
        Instant submittedAt,
        ExaminerAiReferenceResponse aiReference,
        ExaminerDecisionResponse decision,
        String reviewVersion,
        boolean canFinalize,
        String blockingReason) {

    public ExaminerAnswerDetailResponse(
            UUID answerPublicId,
            String taskType,
            ExaminerPromptResponse prompt,
            ExaminerAnswerPayloadResponse response,
            String status,
            Integer myScore,
            Instant submittedAt) {
        this(answerPublicId, taskType, prompt, response, status, myScore, submittedAt,
                null, null, null, true, null);
    }
}
