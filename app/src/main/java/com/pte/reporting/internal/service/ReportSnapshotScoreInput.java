package com.pte.reporting.internal.service;

import com.pte.scoring.dto.response.ItemTraceKind;
import com.pte.scoring.rubric.ScoreTrace;

import java.util.Map;
import java.util.UUID;

/**
 * One answer as it was published. Schema 2 adds the stored Pearson trait scores, the rubric version they were scored
 * with, the stored word count, and the item-score explanation ({@code traceKind}, {@code objectiveRuleCode},
 * {@code itemTrace}); all are null in a schema-1 snapshot.
 */
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
        Integer selectedScore,
        Map<String, Integer> traitScores,
        UUID rubricVersionPublicId,
        Integer wordCount,
        ItemTraceKind traceKind,
        String objectiveRuleCode,
        ScoreTrace itemTrace) {
}
