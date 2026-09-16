package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = IdentityConstants.REFRESH_TOKEN_REQUIRED)
        String refreshToken) {
}
