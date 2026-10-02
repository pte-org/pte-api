package com.pte.scoring.dto.response;

import java.util.UUID;

/** Host-only comparison view; do not reuse as the Examiner or student/report response contract. */
public record HostScoreReviewView(
        UUID answerPublicId,
        UUID attemptPublicId,
        String taskType,
        String section,
        String scoringMethod,
        String status,
        Integer aiRawScore,
        String aiProviderCategory,
        String aiProvider,
        String aiModel,
        String aiProviderVersion,
        boolean aiAvailable,
        Integer examinerScore,
        String examinerStatus,
        UUID assignedExaminerPublicId,
        boolean examinerAvailable,
        Integer teacherScore,
        String selectedScoreSource,
        long lockVersion) {
}
