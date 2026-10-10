package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** Host-editable profile fields for a tenant student. */
public record UpdateStudentProfileRequest(
        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        String fullName,

        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        String phone,

        LocalDate dateOfBirth) {
}
