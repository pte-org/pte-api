package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;

public record ScoreSourceSelectionPreviewResponse(
        String reviewVersion,
        ScoreSourceSelectionScope scope,
        String scopeValue,
        ScoreSource selectedSource,
        int matchedAnswerCount,
        int availableAnswerCount,
        int unavailableAnswerCount,
        int currentAiCount,
        int currentExaminerCount,
        int currentUnselectedCount,
        boolean publicationLocked,
        boolean canApply) {
}
