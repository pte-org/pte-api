package com.pte.scoring.dto.response;

import java.util.UUID;

/**
 * Reporting-only scoring input; deliberately omits Host teacherScore and Examiner-facing answer content.
 * {@code selectedScore} is the publishable score; {@code provisionalScore} is the same score, or the AI score
 * of an answer whose source is not selected yet. Only the pre-publication view may use the latter.
 */
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
        long lockVersion,
        Integer provisionalScore) {

    public ReportScoringAnswerView(UUID answerPublicId, UUID attemptPublicId, UUID scoreTemplatePublicId,
            String taskType, String section, String scoringMethod, Integer aiRawScore, String aiProviderCategory,
            String aiProvider, String aiModel, String aiProviderVersion, Integer examinerScore,
            String selectedScoreSource, Integer selectedScore, boolean publishable, String blockingReason,
            long lockVersion) {
        this(answerPublicId, attemptPublicId, scoreTemplatePublicId, taskType, section, scoringMethod, aiRawScore,
                aiProviderCategory, aiProvider, aiModel, aiProviderVersion, examinerScore, selectedScoreSource,
                selectedScore, publishable, blockingReason, lockVersion, selectedScore);
    }
}
