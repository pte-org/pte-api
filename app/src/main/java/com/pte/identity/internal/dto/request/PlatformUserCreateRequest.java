package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Platform-admin-only creation request; tenant scope is intentionally absent. */
public record PlatformUserCreateRequest(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        String fullName,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String password,

        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROLE_REQUIRED)
        List<String> roles) {
}
