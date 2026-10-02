package com.pte.attempt.internal.dto.request;

import com.pte.attempt.internal.constant.AttemptConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.util.UUID;

/** {@code deviceCheckConfirmed} is a client self-attestation, not a server-verified fact — absent/false always means "not confirmed." */
public record StartAttemptRequest(@NotNull(message = AttemptConstants.SESSION_REFERENCE_REQUIRED) UUID sessionPublicId,
        boolean deviceCheckConfirmed, @Valid ClientCapabilityManifest capabilityManifest) {

    public StartAttemptRequest(UUID sessionPublicId, boolean deviceCheckConfirmed) {
        this(sessionPublicId, deviceCheckConfirmed, null);
    }
}
