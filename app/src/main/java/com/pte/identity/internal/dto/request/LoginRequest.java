package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        String password) {
}
