package com.pte.scoring.dto.response;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Reporting-only scoring input; deliberately omits Host teacherScore and Examiner-facing answer content.
 * {@code selectedScore} is the publishable score; {@code provisionalScore} is the same score, or the AI score
 * of an answer whose source is not selected yet. Only the pre-publication view may use the latter.
 *
 * <p>{@code traitScores} are the stored Pearson trait scores of the AI result and {@code rubricVersionPublicId} the
 * rubric version they were scored with; both are empty/null for objective answers and for legacy rows stored without
 * the {@code PEARSON_V1} scale. {@code wordCount} is the word count stored with that result: the passage length N for
 * Read Aloud (its Content maximum), the response length for a Form-checked writing answer, null otherwise.
 */
public record ReportScoringAnswerView(
        UUID answerPublicId,
        UUID attemptPublicId,
        UUID examDefinitionPublicId,
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
        long lockVersion,
        Integer provisionalScore,
        Map<String, Integer> traitScores,
        UUID rubricVersionPublicId,
        Integer wordCount) {

    public ReportScoringAnswerView {
        traitScores = traitScores == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(traitScores));
    }

    /** Compatibility constructor for callers without stored trait scores. */
    public ReportScoringAnswerView(UUID answerPublicId, UUID attemptPublicId, UUID examDefinitionPublicId,
            String taskType, String section, String scoringMethod, Integer aiRawScore, String aiProviderCategory,
            String aiProvider, String aiModel, String aiProviderVersion, Integer examinerScore,
            String selectedScoreSource, Integer selectedScore, boolean publishable, String blockingReason,
            long lockVersion, Integer provisionalScore) {
        this(answerPublicId, attemptPublicId, examDefinitionPublicId, taskType, section, scoringMethod, aiRawScore,
                aiProviderCategory, aiProvider, aiModel, aiProviderVersion, examinerScore, selectedScoreSource,
                selectedScore, publishable, blockingReason, lockVersion, provisionalScore, Map.of(), null, null);
    }

    public ReportScoringAnswerView(UUID answerPublicId, UUID attemptPublicId, UUID examDefinitionPublicId,
            String taskType, String section, String scoringMethod, Integer aiRawScore, String aiProviderCategory,
            String aiProvider, String aiModel, String aiProviderVersion, Integer examinerScore,
            String selectedScoreSource, Integer selectedScore, boolean publishable, String blockingReason,
            long lockVersion) {
        this(answerPublicId, attemptPublicId, examDefinitionPublicId, taskType, section, scoringMethod, aiRawScore,
                aiProviderCategory, aiProvider, aiModel, aiProviderVersion, examinerScore, selectedScoreSource,
                selectedScore, publishable, blockingReason, lockVersion, selectedScore);
    }
}
