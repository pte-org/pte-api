package com.pte.scoring.dto.response;

import java.util.List;

public record HostScoreReviewResponse(
        String reviewVersion,
        boolean publicationLocked,
        int aiEligibleAttemptCount,
        int assignedAttemptCount,
        int unassignedAttemptCount,
        int pendingExaminerAnswerCount,
        int unavailableSelectedAnswerCount,
        List<HostScoreReviewView> answers,
        String markingMode,
        String cohortStatus,
        boolean canSelectScoreSource) {

    public HostScoreReviewResponse(
            String reviewVersion,
            boolean publicationLocked,
            int aiEligibleAttemptCount,
            int assignedAttemptCount,
            int unassignedAttemptCount,
            int pendingExaminerAnswerCount,
            int unavailableSelectedAnswerCount,
            List<HostScoreReviewView> answers) {
        this(reviewVersion, publicationLocked, aiEligibleAttemptCount, assignedAttemptCount,
                unassignedAttemptCount, pendingExaminerAnswerCount, unavailableSelectedAnswerCount,
                answers, null, null, !publicationLocked);
    }
}
