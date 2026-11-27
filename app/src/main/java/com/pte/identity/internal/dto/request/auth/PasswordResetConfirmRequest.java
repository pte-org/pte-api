package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Spends a reset link and chooses the new password. */
public record PasswordResetConfirmRequest(
        @NotBlank(message = IdentityConstants.RESET_TOKEN_REQUIRED)
        String token,

        @NotBlank(message = IdentityConstants.NEW_PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        @Pattern(regexp = IdentityConstants.PASSWORD_NO_EDGE_SPACES_REGEX, message = IdentityConstants.PASSWORD_EDGE_SPACES)
        @MaxUtf8Bytes(value = IdentityConstants.PASSWORD_MAX_BYTES, message = IdentityConstants.PASSWORD_TOO_LONG)
        String newPassword) {
}
