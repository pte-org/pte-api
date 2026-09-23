package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ExaminerAssignmentBatchSummaryResponse(
        UUID batchPublicId,
        String mode,
        String status,
        int attemptCount,
        int eligibleAnswerCount,
        Instant createdAt,
        Instant previewExpiresAt,
        Instant committedAt) {
}
