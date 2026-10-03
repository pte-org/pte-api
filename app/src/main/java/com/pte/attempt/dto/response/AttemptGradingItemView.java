package com.pte.attempt.dto.response;

import java.util.UUID;

/** Pinned item identity crossing the attempt/scoring boundary for coverage checks. */
public record AttemptGradingItemView(UUID pinnedItemPublicId, UUID scoreTemplatePublicId, String taskType) {
}
