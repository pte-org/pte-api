package com.pte.attempt.dto.response;

import java.util.List;
import java.util.UUID;

/** Full expected item set for one immutable attempt snapshot. */
public record AttemptGradingCoverageView(UUID attemptPublicId, List<AttemptGradingItemView> expectedItems) {
    public AttemptGradingCoverageView {
        expectedItems = expectedItems == null ? List.of() : List.copyOf(expectedItems);
    }
}
