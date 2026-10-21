package com.pte.scoring.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * One row per eligible submitted attempt. {@code locked} turns true once the Host has committed the assignment;
 * {@code examinerPublicId} is only filled for committed rows.
 */
public record ExaminerAssignmentRosterResponse(boolean locked, List<Row> rows) {

    public record Row(UUID attemptPublicId, UUID studentPublicId, String studentName, String studentEmail,
            UUID examinerPublicId) {
    }
}
