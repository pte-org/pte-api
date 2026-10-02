package com.pte.attempt.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public attempt-module view used by the proctoring read adapter. */
public record AttemptSecurityEventView(
        UUID publicId,
        UUID attemptPublicId,
        UUID studentPublicId,
        UUID sessionPublicId,
        String violationType,
        String severity,
        String clientEventId,
        String detail,
        Instant clientOccurredAt,
        Instant detectedAt) {
}
