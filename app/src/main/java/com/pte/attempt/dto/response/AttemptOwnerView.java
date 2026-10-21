package com.pte.attempt.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Who an attempt belongs to, which try it was and when it was submitted (null until then) — the host's submissions list. */
public record AttemptOwnerView(UUID attemptPublicId, UUID studentPublicId, int attemptNumber, Instant submittedAt) {
}
