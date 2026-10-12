package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Assignment-scoped detail; student identity and other examiners' scores are omitted, the AI score is included. */
public record ExaminerAttemptDetailResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        Instant assignedAt,
        int eligibleAnswerCount,
        int submittedAnswerCount,
        List<ExaminerAnswerDetailResponse> answers) {
}
