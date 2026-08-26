package com.pte.iam.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public view of a user — never exposes the entity or password hash. */
public record UserResponse(
        UUID publicId,
        String email,
        String fullName,
        UUID tenantId,
        String status,
        List<String> roles,
        String studentCode,
        String className,
        String phone,
        LocalDate dateOfBirth) {
}
