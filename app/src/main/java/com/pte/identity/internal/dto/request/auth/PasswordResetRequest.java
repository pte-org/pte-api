package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/** Asks for a reset link. Deliberately no {@code @Email} check: every non-blank answer looks the same to the caller. */
public record PasswordResetRequest(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        String email) {
}
