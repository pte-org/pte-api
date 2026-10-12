package com.pte.scoring.internal.dto.response;

import java.util.UUID;

/**
 * One row of the host's submissions list: a student's attempt with how far its answers got through scoring.
 * {@code studentPublicId} / {@code studentName} / {@code attemptNumber} are null only if the attempt or its
 * student can no longer be resolved.
 */
public record AttemptReviewItemResponse(UUID attemptPublicId, UUID studentPublicId, String studentName,
                                        Integer attemptNumber,
                                        int answerCount, int scoredCount, int pendingCount, int aiScoringCount,
                                        int failedCount) {
}
