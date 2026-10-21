package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Attempt-safe Examiner queue row; intentionally contains no student identity or score values.
 * {@code sessionName} and {@code attemptOrdinal} (1-based position within this Examiner's assignments for the
 * session) replace opaque ids as the human-facing label.
 */
public record ExaminerQueueItemResponse(
        UUID attemptPublicId,
        UUID sessionPublicId,
        Instant assignedAt,
        int eligibleAnswerCount,
        long submittedAnswerCount,
        String status,
        boolean canFinalize,
        String blockingReason,
        boolean hasOutstandingAnswers,
        String sessionName,
        int attemptOrdinal) {

    public ExaminerQueueItemResponse(
            UUID attemptPublicId,
            UUID sessionPublicId,
            Instant assignedAt,
            int eligibleAnswerCount,
            long submittedAnswerCount,
            String status,
            boolean canFinalize,
            String blockingReason,
            boolean hasOutstandingAnswers) {
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, status,
                canFinalize, blockingReason, hasOutstandingAnswers, null, 0);
    }

    public ExaminerQueueItemResponse(
            UUID attemptPublicId,
            UUID sessionPublicId,
            Instant assignedAt,
            int eligibleAnswerCount,
            long submittedAnswerCount,
            String status) {
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, status,
                false, null, false);
    }

    public ExaminerQueueItemResponse withSessionName(String name) {
        return new ExaminerQueueItemResponse(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount,
                submittedAnswerCount, status, canFinalize, blockingReason, hasOutstandingAnswers, name,
                attemptOrdinal);
    }
}
