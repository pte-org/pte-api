package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin-assisted password reset — platform-admin-only, see {@code UserController}. */
public record ResetPasswordRequest(
        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String newPassword) {
}
