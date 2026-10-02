package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Attempt-safe Examiner queue row; intentionally contains no student identity or score values. */
public record ExaminerQueueItemResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        Instant assignedAt,
        int eligibleAnswerCount,
        long submittedAnswerCount,
        String status) {
}
