package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/** Asks whether a reset link can still be used. The token travels in the body, never in a URL that gets logged. */
public record PasswordResetValidateRequest(
        @NotBlank(message = IdentityConstants.RESET_TOKEN_REQUIRED)
        String token) {
}
