package com.pte.scoring.dto.response;

import java.util.UUID;

/** Examiner-blind work contract: intentionally excludes every score, source decision, and AI provenance field. */
public record ExaminerScoringWorkItemView(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID sessionPublicId,
        UUID pinnedItemPublicId,
        String taskType,
        String responsePayload) {
}
