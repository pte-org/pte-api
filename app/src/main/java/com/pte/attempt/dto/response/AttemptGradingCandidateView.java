package com.pte.attempt.dto.response;

import java.util.UUID;

/** Attempt lifecycle candidate of a closed session; {@code excluded} is the host's disposition once grading is frozen. */
public record AttemptGradingCandidateView(UUID attemptPublicId, UUID studentPublicId,
        String status, String versionToken, boolean excluded, String exclusionReason) {
    public boolean submitted() {
        return "SUBMITTED".equals(status);
    }
}
