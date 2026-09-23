package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Preview/confirm summary deliberately contains no student identity or answer content. */
public record ExaminerAssignmentPreviewResponse(
        UUID batchPublicId,
        String mode,
        String status,
        boolean valid,
        boolean supplemental,
        int attemptCount,
        int eligibleAnswerCount,
        List<ExaminerAssignmentLoadResponse> examinerLoads,
        List<ExaminerAssignmentConflictResponse> conflicts,
        Instant previewExpiresAt,
        Instant committedAt) {
}
