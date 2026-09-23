package com.pte.scoring.dto.response;

import java.util.List;

public record ExaminerAssignmentOverviewResponse(
        List<ExaminerAssignmentBatchSummaryResponse> batches,
        List<ExaminerAssignmentLoadResponse> committedExaminerLoads,
        long assignedAttemptCount,
        int page,
        int size,
        long totalBatches,
        int totalPages) {
}
