package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

/** Student-safe conflict detail: attempt and selected scope IDs only. */
public record ExaminerAssignmentConflictResponse(
        UUID attemptPublicId,
        List<ExaminerAssignmentScopeReferenceResponse> conflictingScopes) {
}
