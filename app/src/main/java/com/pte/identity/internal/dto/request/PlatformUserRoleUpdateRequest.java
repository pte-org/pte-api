package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Replaces a managed platform user's assignable role set. */
public record PlatformUserRoleUpdateRequest(
        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROLE_REQUIRED)
        List<String> roles) {
}
