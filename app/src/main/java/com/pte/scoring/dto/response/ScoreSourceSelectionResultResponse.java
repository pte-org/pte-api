package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;

import java.time.Instant;
import java.util.UUID;

public record ScoreSourceSelectionResultResponse(
        UUID auditPublicId,
        UUID requestPublicId,
        ScoreSourceSelectionScope scope,
        String scopeValue,
        ScoreSource selectedSource,
        int affectedAnswerCount,
        int previousAiCount,
        int previousExaminerCount,
        int previousUnselectedCount,
        Instant occurredAt,
        boolean replayed) {
}
