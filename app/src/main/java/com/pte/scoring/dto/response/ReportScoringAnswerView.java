package com.pte.scoring.dto.response;

import java.util.UUID;

/** Reporting-only scoring input; deliberately omits Host teacherScore and Examiner-facing answer content. */
public record ReportScoringAnswerView(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID scoreTemplatePublicId,
        String taskType,
        String section,
        String scoringMethod,
        Integer aiRawScore,
        String aiProviderCategory,
        String aiProvider,
        String aiModel,
        String aiProviderVersion,
        Integer examinerScore,
        String selectedScoreSource,
        Integer selectedScore,
        boolean publishable,
        String blockingReason,
        long lockVersion) {
}
