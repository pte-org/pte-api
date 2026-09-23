package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;

import java.util.UUID;

public record SelectScoreSourceRequest(
        ScoreSourceSelectionScope scope,
        String scopeValue,
        ScoreSource selectedSource,
        String expectedReviewVersion,
        UUID requestPublicId) {
}
