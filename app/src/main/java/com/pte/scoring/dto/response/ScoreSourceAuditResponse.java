package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;

import java.time.Instant;
import java.util.UUID;

public record ScoreSourceAuditResponse(
        UUID auditPublicId,
        UUID requestPublicId,
        UUID actorPublicId,
        ScoreSourceSelectionScope scope,
        String scopeValue,
        ScoreSource selectedSource,
        int affectedAnswerCount,
        int previousAiCount,
        int previousExaminerCount,
        int previousUnselectedCount,
        Instant occurredAt) {
}
