package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;

/** Credentials used to discover which tenant should disambiguate a login. */
public record LoginOrganizationOptionsRequest(
        @NotBlank(message = IdentityConstants.USERNAME_REQUIRED)
        String username,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        String password) {
}
