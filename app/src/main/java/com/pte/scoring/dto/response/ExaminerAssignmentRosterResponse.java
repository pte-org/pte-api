package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;

import java.util.List;
import java.util.UUID;

/**
 * One row per eligible submitted attempt. {@code locked} turns true once the Host has committed the assignment;
 * {@code examinerPublicId}, {@code workStatus} and {@code draftCount} are only filled for committed rows, and
 * {@code reassignable} says whether the Host may still hand the attempt to another Examiner.
 */
public record ExaminerAssignmentRosterResponse(boolean locked, List<Row> rows) {

    public record Row(UUID attemptPublicId, UUID studentPublicId, String studentName, String studentEmail,
            UUID examinerPublicId, ExaminerWorkflowStatus workStatus, int draftCount, boolean reassignable) {
    }
}
