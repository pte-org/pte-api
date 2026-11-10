package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password change requested by the currently authenticated user. {@code refreshToken} is the caller's own session:
 * when present, every other session is signed out and this one is kept. Clients that predate it omit the field and
 * no session is revoked.
 */
public record ChangePasswordRequest(
        @NotBlank(message = IdentityConstants.CURRENT_PASSWORD_REQUIRED)
        String currentPassword,

        @NotBlank(message = IdentityConstants.NEW_PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String newPassword,

        String refreshToken) {

    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, null);
    }
}
