package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/**
 * Asks for a reset link for one account. Deliberately no {@code @Email} check: every non-blank answer looks the same
 * to the caller. The username is required because one e-mail can belong to several accounts: the link goes to the
 * one account that has both.
 */
public record PasswordResetRequest(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        String email,
        @NotBlank(message = IdentityConstants.USERNAME_REQUIRED)
        String username) {
}
