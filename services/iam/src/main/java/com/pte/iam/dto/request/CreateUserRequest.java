package com.pte.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create a host/proctor/student (or, for a platform caller, a host/platform user).
 * {@code tenantId} is honored ONLY for platform callers; a tenant-scoped caller's
 * created users are forced into the caller's own tenant (service enforces this).
 * The four profile fields are optional and apply to any role, not just STUDENT.
 */
public record CreateUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotEmpty(message = "At least one role is required")
        List<String> roles,

        UUID tenantId,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
