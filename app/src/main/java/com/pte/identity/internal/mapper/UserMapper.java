package com.pte.identity.internal.mapper;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.dto.response.UserResponse;

/** Maps accounts to their response DTO. Never leaks the entity outward. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return toResponse(user, null, null);
    }

    public static UserResponse toResponse(User user, String organizationType, String organizationName) {
        return new UserResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getOrganizationId(),
                user.getStatus().name(),
                user.getRoles().stream().map(Role::name).toList(),
                user.getStudentCode(),
                user.getClassName(),
                user.getPhone(),
                user.getDateOfBirth(),
                organizationType,
                organizationName,
                user.isMustChangePassword(),
                user.isDeleted());
    }
}
