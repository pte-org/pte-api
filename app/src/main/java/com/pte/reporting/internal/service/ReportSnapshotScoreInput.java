package com.pte.reporting.internal.service;

import java.util.UUID;

public record ReportSnapshotScoreInput(
        UUID answerPublicId,
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
        Integer selectedScore) {
}
