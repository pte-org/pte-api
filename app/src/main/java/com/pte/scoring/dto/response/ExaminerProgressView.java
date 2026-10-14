package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

public record ExaminerProgressView(UUID sessionPublicId, long assignedAttemptCount, long batchCount,
        Long frozenEligibleAnswerCount, Long eligibleAnswerCount, Long submittedAnswerCount,
        long pendingAttemptCount, long inProgressAttemptCount, long completedAttemptCount,
        long unavailableAttemptCount, String workStatus, String publicationStatus,
        Integer progressPercent, List<String> diagnosticCodes) {
}
