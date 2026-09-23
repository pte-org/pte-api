package com.pte.scoring.dto.response;

import java.util.UUID;

/** Host-only comparison view; do not reuse as the Examiner or student/report response contract. */
public record HostScoreReviewView(
        UUID answerPublicId,
        UUID attemptPublicId,
        String taskType,
        String status,
        Integer aiRawScore,
        String aiProviderCategory,
        Integer examinerScore,
        Integer teacherScore,
        String selectedScoreSource) {
}
