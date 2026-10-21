package com.pte.identity;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public, immutable identity snapshot for cross-module student workspace reads. */
public record StudentIdentityView(
        UUID publicId,
        String username,
        String email,
        String fullName,
        UUID tenantId,
        String status,
        List<String> roles,
        String studentCode,
        String phone,
        LocalDate dateOfBirth,
        boolean mustChangePassword) {

    public static StudentIdentityView from(User user) {
        return new StudentIdentityView(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getTenantId(),
                user.getStatus().name(),
                user.getRoles().stream().map(Role::name).sorted().toList(),
                user.getStudentCode(),
                user.getPhone(),
                user.getDateOfBirth(),
                user.isMustChangePassword());
    }
}
