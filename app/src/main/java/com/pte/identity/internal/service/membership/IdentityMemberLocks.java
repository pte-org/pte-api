package com.pte.identity.internal.service.membership;

import com.pte.identity.IdentityService.IdentityNotEligibleException;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Row locks that serialize role-owned writes in other modules against identity changes. Each lock is taken inside
 * the caller's transaction (the facade declares {@code MANDATORY} propagation) in deterministic id order.
 */
public class IdentityMemberLocks {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;

    public IdentityMemberLocks(UserRepository userRepository, OrganizationMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public boolean lockActiveRoleMember(UUID publicId, UUID tenantId, Role role) {
        if (publicId == null || role == null) {
            return false;
        }
        if (tenantId == null) {
            return userRepository.findWithLockByPublicId(publicId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getRoles().contains(role))
                    .isPresent();
        }
        return memberRepository.findWithLockByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive() && member.hasRole(role))
                .isPresent();
    }

    public void lockActiveStudentsInTenant(Collection<UUID> publicIds, UUID tenantId) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()
                || publicIds.stream().anyMatch(Objects::isNull)) {
            throw new IdentityNotEligibleException();
        }
        List<UUID> orderedIds = publicIds.stream().distinct().sorted().toList();
        List<OrganizationMember> locked = memberRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId);
        Set<UUID> lockedIds = locked.stream().map(member -> member.getUser().getPublicId())
                .collect(Collectors.toSet());
        if (locked.size() != orderedIds.size()
                || !lockedIds.equals(Set.copyOf(orderedIds))
                || locked.stream().anyMatch(member -> !member.isActive()
                        || !member.hasRole(Role.STUDENT))) {
            throw new IdentityNotEligibleException();
        }
    }

    public List<ExaminerIdentityView> lockActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        List<UUID> orderedIds = examinerPublicIds.stream().distinct().sorted().toList();
        return memberRepository.findWithLockByUsersAndOrganization(orderedIds, tenantId).stream()
                .filter(member -> member.isActive() && member.hasRole(Role.EXAMINER))
                .map(IdentityMemberQuery::toExaminerView)
                .toList();
    }
}
