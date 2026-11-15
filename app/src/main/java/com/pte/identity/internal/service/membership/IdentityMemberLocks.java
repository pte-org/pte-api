package com.pte.identity.internal.service.membership;

import com.pte.identity.IdentityService.IdentityNotEligibleException;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.internal.repository.UserRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Row locks that serialize role-owned writes in other modules against identity changes. Each lock is taken inside
 * the caller's transaction (the facade declares {@code MANDATORY} propagation) in deterministic public-id order.
 */
public class IdentityMemberLocks {

    private final UserRepository userRepository;

    public IdentityMemberLocks(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean lockActiveRoleMember(UUID publicId, UUID tenantId, Role role) {
        if (publicId == null || role == null) {
            return false;
        }
        if (tenantId == null) {
            return userRepository.findWithLockByPublicId(publicId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getOrganizationId() == null
                            && user.getRoles().contains(role))
                    .isPresent();
        }
        return userRepository.findWithLockByUserAndOrganization(publicId, tenantId)
                .filter(user -> user.isActive() && user.hasRole(role))
                .isPresent();
    }

    public void lockActiveStudentsInTenant(Collection<UUID> publicIds, UUID tenantId) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()
                || publicIds.stream().anyMatch(Objects::isNull)) {
            throw new IdentityNotEligibleException();
        }
        List<UUID> orderedIds = publicIds.stream().distinct().sorted().toList();
        List<User> locked = userRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId);
        Set<UUID> lockedIds = locked.stream().map(User::getPublicId).collect(Collectors.toSet());
        if (locked.size() != orderedIds.size()
                || !lockedIds.equals(Set.copyOf(orderedIds))
                || locked.stream().anyMatch(user -> !user.isActive() || !user.hasRole(Role.STUDENT))) {
            throw new IdentityNotEligibleException();
        }
    }

    public List<ExaminerIdentityView> lockActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        List<UUID> orderedIds = examinerPublicIds.stream().distinct().sorted().toList();
        return userRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId).stream()
                .filter(user -> user.isActive() && user.hasRole(Role.EXAMINER))
                .map(IdentityMemberQuery::toExaminerView)
                .toList();
    }
}
