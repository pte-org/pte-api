package com.pte.scoring.internal.dto.response;

import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;

import java.util.UUID;

/**
 * The examiner assigned to an attempt and how far they are: {@code savedCount} of {@code eligibleCount} answers
 * have a score saved, and {@code status} says whether they have not started, are working, or finalized the attempt.
 */
public record AttemptExaminerProgress(UUID examinerPublicId, ExaminerWorkflowStatus status, int eligibleCount,
                                      int savedCount) {
}
