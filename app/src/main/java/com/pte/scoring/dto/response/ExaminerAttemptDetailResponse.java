package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Assignment-scoped detail; student identity and other examiners' scores are omitted, the AI score is included.
 * {@code sessionName} and {@code attemptOrdinal} are the human-facing labels shown instead of opaque ids.
 */
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
        String blockingReason,
        ExaminerWorkflowView workflow,
        String sessionName,
        int attemptOrdinal) {

    public ExaminerAttemptDetailResponse(
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
            String blockingReason,
            ExaminerWorkflowView workflow) {
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, answers,
                cohortMode, cohortStatus, publicationState, canFinalize, blockingReason, workflow, null, 0);
    }

    public ExaminerAttemptDetailResponse(
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
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, answers,
                cohortMode, cohortStatus, publicationState, canFinalize, blockingReason, null);
    }

    public ExaminerAttemptDetailResponse(
            UUID attemptPublicId,
            UUID sessionPublicId,
            Instant assignedAt,
            int eligibleAnswerCount,
            int submittedAnswerCount,
            List<ExaminerAnswerDetailResponse> answers) {
        this(attemptPublicId, sessionPublicId, assignedAt, eligibleAnswerCount, submittedAnswerCount, answers,
                null, null, "UNPUBLISHED", true, null, null);
    }
}
