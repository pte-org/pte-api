package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;

import java.time.LocalDate;

/** One roster row for {@link BulkCreateUsersRequest} — always created as STUDENT, no password (server-generated).
 * Email and profile fields are optional; the server generates the login username when email is absent.
 */
public record BulkCreateUserRow(
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        String fullName,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
