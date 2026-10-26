package com.pte.session.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Grading state of a closed session as other modules see it; the marking mode is the owner module's own value. */
public record SessionGradingView(UUID sessionPublicId, UUID tenantId, String markingMode, String status,
                                 String previewVersion, UUID frozenByPublicId, Instant frozenAt,
                                 Instant completedAt) {
}
