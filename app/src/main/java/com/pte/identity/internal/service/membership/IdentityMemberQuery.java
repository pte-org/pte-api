package com.pte.identity.internal.service.membership;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.StudentIdentityView;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.identity.internal.repository.UserRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Organization-scoped identity reads behind the {@code IdentityService} facade. Every read filters on the account's
 * organization; the facade owns the transaction boundaries.
 */
public class IdentityMemberQuery {

    private final UserRepository userRepository;

    public IdentityMemberQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<StudentIdentityView> findStudentByTenant(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            return Optional.empty();
        }
        return userRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(user -> user.hasRole(Role.STUDENT))
                .map(StudentIdentityView::from);
    }

    public boolean isMemberOf(UUID userPublicId, UUID organizationId) {
        return userPublicId != null && organizationId != null
                && userRepository.findActiveByUserAndOrganization(userPublicId, organizationId).isPresent();
    }

    public List<User> findByTenantIdAndRole(UUID tenantId, Role role) {
        return userRepository.findActiveByOrganizationAndRole(tenantId, role);
    }

    public List<User> findActiveByTenantIdAndRole(UUID tenantId, Role role) {
        return findByTenantIdAndRole(tenantId, role).stream()
                .filter(user -> !user.isDeleted() && user.getStatus() == UserStatus.ACTIVE)
                .toList();
    }

    public Optional<User> findActiveStaffInTenant(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            return Optional.empty();
        }
        return userRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(user -> user.isActive() && (user.hasRole(Role.PROCTOR) || user.hasRole(Role.EXAMINER)));
    }

    public Set<UUID> findActiveStudentPublicIds(UUID tenantId, Collection<UUID> publicIds) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> targetIds = publicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .filter(User::isActive)
                .filter(user -> user.hasRole(Role.STUDENT))
                .map(User::getPublicId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<IdentityRoleMember> findActiveRoleMembers(Role role) {
        if (role == null) {
            return List.of();
        }
        return userRepository.findActiveByRole(role, UserStatus.ACTIVE).stream()
                .map(user -> new IdentityRoleMember(user.getPublicId(), user.getOrganizationId()))
                .toList();
    }

    public List<IdentityRoleMember> findActiveRoleMembers(UUID tenantId, Role role) {
        if (tenantId == null || role == null) {
            return List.of();
        }
        return userRepository.findActiveByOrganizationAndRole(tenantId, role).stream()
                .filter(User::isActive)
                .map(user -> new IdentityRoleMember(user.getPublicId(), user.getOrganizationId()))
                .toList();
    }

    public List<ExaminerIdentityView> findActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findActiveByUsersAndOrganization(examinerPublicIds, tenantId).stream()
                .filter(user -> user.isActive() && user.hasRole(Role.EXAMINER))
                .map(IdentityMemberQuery::toExaminerView)
                .toList();
    }

    public Map<UUID, String> findFullNames(UUID tenantId, Collection<UUID> userPublicIds) {
        if (tenantId == null || userPublicIds == null || userPublicIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findActiveByUsersAndOrganization(List.copyOf(userPublicIds), tenantId).stream()
                .collect(Collectors.toMap(User::getPublicId, User::getFullName, (existing, replacement) -> existing));
    }

    public Map<UUID, StudentDisplayNameView> getStudentDisplayNames(UUID tenantId,
            Collection<UUID> studentPublicIds) {
        if (tenantId == null || studentPublicIds == null || studentPublicIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UUID> targetIds = studentPublicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .collect(Collectors.toMap(
                        User::getPublicId,
                        user -> {
                            String name = (user.getFullName() != null && !user.getFullName().isBlank())
                                    ? user.getFullName()
                                    : user.getUsername();
                            return new StudentDisplayNameView(user.getPublicId(), name, user.getEmail());
                        },
                        (existing, replacement) -> existing));
    }

    public Optional<User> findActiveInTenant(UUID publicId, UUID tenantId, Role role) {
        if (publicId == null || tenantId == null || role == null) {
            return Optional.empty();
        }
        return userRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(user -> user.isActive() && user.hasRole(role));
    }

    static ExaminerIdentityView toExaminerView(User user) {
        return new ExaminerIdentityView(user.getPublicId(), user.getFullName(), user.getEmail());
    }
}
