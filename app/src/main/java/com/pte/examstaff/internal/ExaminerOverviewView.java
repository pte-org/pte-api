package com.pte.examstaff.internal;

import java.util.Map;

public record ExaminerOverviewView(long totalSessions, Map<String, Long> totalByStatus, long assignedAttemptCount,
        long batchCount, Long eligibleAnswerCount, Long submittedAnswerCount, Integer progressPercent,
        long pendingAttemptCount, long inProgressAttemptCount, long completedAttemptCount,
        long unavailableAttemptCount, long verifiedEligibleAnswerCount, long verifiedSubmittedAnswerCount,
        boolean progressCoverageComplete) { }
