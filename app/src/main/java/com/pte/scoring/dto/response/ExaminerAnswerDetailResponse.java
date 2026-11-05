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
        ExaminerAiReferenceResponse aiReference,
        ExaminerDecisionResponse decision,
        String reviewVersion,
        boolean canFinalize,
        String blockingReason,
        AiScoreDetailResponse ai,
        ExaminerRubricResponse rubric) {
    public ExaminerAnswerDetailResponse(UUID answerPublicId,
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
        String blockingReason,
        AiScoreDetailResponse ai) {
        this(answerPublicId, taskType, prompt, response, status, myScore, submittedAt, aiReference, decision, reviewVersion, canFinalize, blockingReason, ai, null);
    }


    public ExaminerAnswerDetailResponse(
            UUID answerPublicId,
            String taskType,
            ExaminerPromptResponse prompt,
            ExaminerAnswerPayloadResponse response,
            String status,
            Integer myScore,
            Instant submittedAt) {
        this(answerPublicId, taskType, prompt, response, status, myScore, submittedAt,
                null, null, null, true, null, null);
    }
}
