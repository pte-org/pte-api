package com.pte.identity.internal.dto.request.auth;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code username}, not {@code email}: a staff or student username is
 * {@code {tenant.code}.{random}}, not an email address, so no {@code @Email}
 * format check here. The username alone identifies the account and therefore
 * its organization, so no organization is sent at login.
 */
public record LoginRequest(
        @NotBlank(message = IdentityConstants.USERNAME_REQUIRED)
        String username,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        String password) {
}
