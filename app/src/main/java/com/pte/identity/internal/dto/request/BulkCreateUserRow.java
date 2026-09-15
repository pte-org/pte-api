package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** One roster row for {@link BulkCreateUsersRequest} — always created as STUDENT, no password (server-generated). */
public record BulkCreateUserRow(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        String fullName,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
