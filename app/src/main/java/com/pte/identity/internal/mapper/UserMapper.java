package com.pte.identity.internal.mapper;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.dto.response.UserResponse;

import java.util.List;

/** Maps accounts to their response DTO. Never leaks the entity outward. */
public final class UserMapper {

    private UserMapper() {
    }

    /** An account seen on its own (platform users): no organization, only the account's own roles. */
    public static UserResponse toResponse(User user) {
        return toResponse(user, null, user.getRoles().stream().map(Role::name).toList(), null,
                user.getStatus().name());
    }

    /** An account seen through its membership in one organization. */
    public static UserResponse toResponse(OrganizationMember member) {
        return toResponse(member, null);
    }

    public static UserResponse toResponse(OrganizationMember member, String organizationType) {
        return toResponse(member.getUser(), member.getOrganizationId(),
                member.getRoles().stream().map(Role::name).toList(), organizationType,
                (member.isSuspended() ? UserStatus.SUSPENDED : UserStatus.ACTIVE).name());
    }

    /** The account as it signed in: with the selected organization's roles, or on its own. */
    public static UserResponse toResponse(User user, OrganizationMember member, String organizationType) {
        return member == null ? toResponse(user, null, user.getRoles().stream().map(Role::name).toList(),
                organizationType, user.getStatus().name()) : toResponse(member, organizationType);
    }

    private static UserResponse toResponse(User user, java.util.UUID organizationId, List<String> roles,
            String organizationType, String status) {
        return new UserResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                organizationId,
                status,
                roles,
                user.getStudentCode(),
                user.getClassName(),
                user.getPhone(),
                user.getDateOfBirth(),
                organizationType,
                user.isMustChangePassword(),
                user.isDeleted());
    }
}
