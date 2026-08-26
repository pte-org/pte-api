package com.pte.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/** One roster row for {@link BulkCreateUsersRequest} — always created as STUDENT, no password (server-generated). */
public record BulkCreateUserRow(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
