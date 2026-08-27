package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code deviceCheckConfirmed} is a client self-attestation, not a server-verified fact — absent/false always means "not confirmed." */
public record StartAttemptRequest(@NotNull(message = "Session reference is required") UUID sessionPublicId,
        boolean deviceCheckConfirmed) {
}
