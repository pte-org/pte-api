package com.pte.identity.internal.dto.request.profile;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Profile fields that a platform administrator may change for a managed platform user. */
public record UpdatePlatformUserProfileRequest(
        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        @Size(max = 255, message = IdentityConstants.FULL_NAME_TOO_LONG)
        String fullName,
        @Size(max = 255, message = IdentityConstants.PHONE_TOO_LONG)
        String phone,
        @PastOrPresent(message = IdentityConstants.DATE_OF_BIRTH_NOT_IN_FUTURE)
        LocalDate dateOfBirth) {
}
