package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code username}, not {@code email}: a STUDENT's username is
 * {@code {tenant.code}.{random}}, not an email address (plans/quang-tenant-
 * commercialization Phase 8), so no {@code @Email} format check here.
 */
public record LoginRequest(
        @NotBlank(message = IdentityConstants.USERNAME_REQUIRED)
        String username,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        String password) {
}
