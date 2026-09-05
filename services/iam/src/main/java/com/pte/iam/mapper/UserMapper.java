package com.pte.iam.mapper;

import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.dto.response.UserResponse;

import java.util.List;

/** Maps the {@link User} entity to its response DTO. Never leaks the entity outward. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream().map(Role::name).toList();
        return new UserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getFullName(),
                user.getTenantId(),
                user.getStatus().name(),
                roles,
                user.getStudentCode(),
                user.getClassName(),
                user.getPhone(),
                user.getDateOfBirth());
    }
}
