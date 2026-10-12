package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Assignment-scoped detail; student identity and other scoring sources are omitted. */
public record ExaminerAttemptDetailResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        Instant assignedAt,
        int eligibleAnswerCount,
        int submittedAnswerCount,
        List<ExaminerAnswerDetailResponse> answers,
        String cohortMode,
        String cohortStatus,
        String publicationState,
        boolean canFinalize,
        String blockingReason) {

    public ExaminerAttemptDetailResponse(
            UUID attemptPublicId,
            UUID sessionPublicId,
            Instant assignedAt,
            int eligibleAnswerCount,
            int submittedAnswerCount,
            List<ExaminerAnswerDetailResponse> answers) {
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, answers,
                null, null, "UNPUBLISHED", true, null);
    }
}
