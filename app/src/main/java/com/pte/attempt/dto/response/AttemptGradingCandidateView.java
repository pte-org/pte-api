package com.pte.attempt.dto.response;

import java.util.UUID;

/** Immutable attempt lifecycle candidate used to freeze a host grading cohort. */
public record AttemptGradingCandidateView(UUID attemptPublicId, UUID studentPublicId,
        String status, String versionToken) {
    public boolean submitted() {
        return "SUBMITTED".equals(status);
    }
}
