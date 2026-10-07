package com.pte.practice.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Does not contain the verification code; delivery is email-only. */
public record PracticeChallengeResponse(UUID challengeId, Instant expiresAt) {
}
