package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Spends a reset link and chooses the new password. */
public record PasswordResetConfirmRequest(
        @NotBlank(message = IdentityConstants.RESET_TOKEN_REQUIRED)
        String token,

        @NotBlank(message = IdentityConstants.NEW_PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String newPassword) {
}
