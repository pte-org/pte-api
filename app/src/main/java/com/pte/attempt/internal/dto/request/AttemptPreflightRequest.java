package com.pte.attempt.internal.dto.request;

import com.pte.attempt.internal.constant.AttemptConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttemptPreflightRequest(
        @NotNull(message = AttemptConstants.SESSION_REFERENCE_REQUIRED) UUID sessionPublicId,
        @Valid ClientCapabilityManifest capabilityManifest) {
}
