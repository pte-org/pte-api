package com.pte.scoring.dto.response;

import java.util.List;

/** What the host sees of a session's examiner assignment: each examiner's load and how many attempts are assigned. */
public record ExaminerAssignmentOverviewResponse(
        List<ExaminerAssignmentLoadResponse> committedExaminerLoads,
        long assignedAttemptCount) {
}
