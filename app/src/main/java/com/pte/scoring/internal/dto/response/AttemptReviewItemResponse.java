package com.pte.scoring.internal.dto.response;

import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the host's submissions list: a student's attempt with how far its answers got through scoring.
 * {@code studentPublicId} / {@code studentName} / {@code attemptNumber} / {@code submittedAt} are null only if the attempt or its
 * student can no longer be resolved.
 * <p>
 * {@code answerCount} and the status counts cover only AI-scored answers (speech and text): all zero when the attempt
 * has none, e.g. a blank or purely objective attempt.
 * <p>
 * The {@code examiner*} fields describe the assigned examiner's marking and are null / zero while the attempt has no
 * examiner: {@code examinerSavedCount} of {@code examinerEligibleCount} answers have a saved score.
 */
public record AttemptReviewItemResponse(UUID attemptPublicId, UUID studentPublicId, String studentName,
                                        Integer attemptNumber,
                                        Instant submittedAt,
                                        int answerCount, int scoredCount, int pendingCount, int aiScoringCount,
                                        int failedCount,
                                        UUID examinerPublicId, String examinerName,
                                        ExaminerWorkflowStatus examinerStatus,
                                        int examinerEligibleCount, int examinerSavedCount) {
}
