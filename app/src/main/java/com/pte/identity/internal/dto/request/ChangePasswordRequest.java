package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password change requested by the currently authenticated user. */
public record ChangePasswordRequest(
        @NotBlank(message = IdentityConstants.CURRENT_PASSWORD_REQUIRED)
        String currentPassword,

        @NotBlank(message = IdentityConstants.NEW_PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String newPassword) {
}
