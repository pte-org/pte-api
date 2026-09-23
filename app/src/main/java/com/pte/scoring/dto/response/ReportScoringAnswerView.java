package com.pte.scoring.dto.response;

import java.util.UUID;

/** Reporting-only scoring input; deliberately omits Host teacherScore and Examiner-facing answer content. */
public record ReportScoringAnswerView(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID scoreTemplatePublicId,
        String taskType,
        String scoringMethod,
        Integer aiRawScore,
        String aiProviderCategory,
        Integer examinerScore,
        String selectedScoreSource) {
}
