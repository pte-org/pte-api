package com.aptis.modules.iam.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String credential,
        @NotBlank String password) {
}
