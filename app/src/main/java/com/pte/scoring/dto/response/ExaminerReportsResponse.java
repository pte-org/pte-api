package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExaminerReportsResponse(
        ExaminerReportSummary summary,
        List<ExaminerReportTaskTypeBreakdown> taskTypeBreakdowns,
        List<ExaminerReportRecentItem> recentFinalizedItems
) {
    public record ExaminerReportSummary(
            long finalizedCount,
            long inProgressCount,
            long pendingCount,
            long totalAssignedCount,
            double avgGradingDurationMinutes,
            long regradeCount
    ) {}

    public record ExaminerReportTaskTypeBreakdown(
            String taskType,
            long totalQuestions,
            long scoredQuestions,
            Double averageScore
    ) {}

    public record ExaminerReportRecentItem(
            UUID attemptPublicId,
            UUID sessionPublicId,
            Instant finalizedAt,
            long scoreCount,
            Double averageScore,
            long workVersion
    ) {}
}
