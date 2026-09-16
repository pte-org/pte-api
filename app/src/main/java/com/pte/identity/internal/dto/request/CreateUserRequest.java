package com.pte.identity.internal.dto.request;

import com.pte.identity.internal.constant.IdentityConstants;
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
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        String fullName,

        @NotBlank(message = IdentityConstants.PASSWORD_REQUIRED)
        @Size(min = 8, message = IdentityConstants.PASSWORD_MIN_LENGTH)
        String password,

        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROLE_REQUIRED)
        List<String> roles,

        UUID tenantId,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
