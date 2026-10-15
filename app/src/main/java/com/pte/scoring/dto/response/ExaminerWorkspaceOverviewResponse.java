package com.pte.scoring.dto.response;

import java.time.Instant;
import java.util.List;

public record ExaminerWorkspaceOverviewResponse(
        Instant asOf,
        String scope,
        ExaminerOverviewCounts counts,
        ExaminerOverviewProgress progress,
        List<ExaminerQueueItemResponse> priorityItems
) {
    public record ExaminerOverviewCounts(
            long pending,
            long inProgress,
            long completed
    ) {
    }

    public record ExaminerOverviewProgress(
            long eligibleAnswerTotal,
            long finalizedAnswerTotal
    ) {
    }
}
