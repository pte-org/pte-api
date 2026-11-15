package com.pte.identity.internal.dto.request.user;

import com.pte.identity.internal.constant.IdentityConstants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create a tenant user (for HOST_ADMIN) or a tenant's HOST_ADMIN account
 * (for PLATFORM_ADMIN).
 * {@code tenantId} is honored ONLY for platform callers; a tenant-scoped caller's
 * created users are forced into the caller's own tenant (service enforces this).
 * Platform callers may only assign HOST_ADMIN; HOST_ADMIN callers may assign
 * the lower tenant roles.
 * The four profile fields are optional and apply to any role, not just STUDENT.
 * There is no credential field: the account gets a generated username and a temporary secret, both e-mailed to the
 * account's address.
 */
public record CreateUserRequest(
        @NotBlank(message = IdentityConstants.EMAIL_REQUIRED)
        @Email(message = IdentityConstants.EMAIL_INVALID)
        String email,

        @NotBlank(message = IdentityConstants.FULL_NAME_REQUIRED)
        String fullName,

        @NotEmpty(message = IdentityConstants.AT_LEAST_ONE_ROLE_REQUIRED)
        List<String> roles,

        UUID tenantId,

        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
