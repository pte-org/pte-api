package com.pte.attempt.dto.response;

import java.util.UUID;

/** Who an attempt belongs to and which try it was — the host's per-attempt submissions list. */
public record AttemptOwnerView(UUID attemptPublicId, UUID studentPublicId, int attemptNumber) {
}
