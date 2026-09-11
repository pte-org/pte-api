package com.pte.iam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin-assisted password reset — platform-admin-only, see {@code UserController}. */
public record ResetPasswordRequest(
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword) {
}
