package com.pte.attempt.dto.response;

import java.util.UUID;

/** attempt's canonical summary of one SUBMITTED attempt — reporting's pull for report creation/publish fanout (Phase 10). */
public record AttemptSummaryView(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId,
                                 UUID snapshotPublicId) {

    /** Compatibility constructor for callers that do not need snapshot provenance. */
    public AttemptSummaryView(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        this(attemptPublicId, sessionPublicId, studentPublicId, tenantId, null);
    }
}
