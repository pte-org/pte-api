package com.pte.identity.internal.service.membership;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.StudentIdentityView;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.identity.internal.repository.OrganizationMemberRepository;

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
 * Organization-scoped identity reads behind the {@code IdentityService} facade. Every read goes through an active
 * {@link OrganizationMember}; the facade owns the transaction boundaries.
 */
public class IdentityMemberQuery {

    private final OrganizationMemberRepository memberRepository;

    public IdentityMemberQuery(OrganizationMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Optional<StudentIdentityView> findStudentByTenant(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            return Optional.empty();
        }
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.hasRole(Role.STUDENT))
                .map(StudentIdentityView::from);
    }

    public boolean isMemberOf(UUID userPublicId, UUID organizationId) {
        return userPublicId != null && organizationId != null
                && memberRepository.findActiveByUserAndOrganization(userPublicId, organizationId).isPresent();
    }

    public List<User> findByTenantIdAndRole(UUID tenantId, Role role) {
        return memberRepository.findActiveByOrganizationAndRole(tenantId, role).stream()
                .map(OrganizationMember::getUser)
                .toList();
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
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive()
                        && (member.hasRole(Role.PROCTOR) || member.hasRole(Role.EXAMINER)))
                .map(OrganizationMember::getUser);
    }

    public Set<UUID> findActiveStudentPublicIds(UUID tenantId, Collection<UUID> publicIds) {
        if (tenantId == null || publicIds == null || publicIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> targetIds = publicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .filter(OrganizationMember::isActive)
                .filter(member -> member.hasRole(Role.STUDENT))
                .map(member -> member.getUser().getPublicId())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<IdentityRoleMember> findActiveRoleMembers(Role role) {
        if (role == null) {
            return List.of();
        }
        return memberRepository.findActiveByRole(role, UserStatus.ACTIVE).stream()
                .map(member -> new IdentityRoleMember(member.getUser().getPublicId(), member.getOrganizationId()))
                .toList();
    }

    public List<IdentityRoleMember> findActiveRoleMembers(UUID tenantId, Role role) {
        if (tenantId == null || role == null) {
            return List.of();
        }
        return memberRepository.findActiveByOrganizationAndRole(tenantId, role).stream()
                .filter(OrganizationMember::isActive)
                .map(member -> new IdentityRoleMember(member.getUser().getPublicId(), member.getOrganizationId()))
                .toList();
    }

    public List<ExaminerIdentityView> findActiveExaminers(UUID tenantId, List<UUID> examinerPublicIds) {
        if (tenantId == null || examinerPublicIds == null || examinerPublicIds.isEmpty()) {
            return List.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(examinerPublicIds, tenantId).stream()
                .filter(member -> member.isActive() && member.hasRole(Role.EXAMINER))
                .map(IdentityMemberQuery::toExaminerView)
                .toList();
    }

    public Map<UUID, String> findFullNames(UUID tenantId, Collection<UUID> userPublicIds) {
        if (tenantId == null || userPublicIds == null || userPublicIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findActiveByUsersAndOrganization(List.copyOf(userPublicIds), tenantId).stream()
                .collect(Collectors.toMap(member -> member.getUser().getPublicId(),
                        member -> member.getUser().getFullName(), (existing, replacement) -> existing));
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
        return memberRepository.findActiveByUsersAndOrganization(targetIds, tenantId).stream()
                .map(OrganizationMember::getUser)
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
        return memberRepository.findActiveByUserAndOrganization(publicId, tenantId)
                .filter(member -> member.isActive() && member.hasRole(role))
                .map(OrganizationMember::getUser);
    }

    static ExaminerIdentityView toExaminerView(OrganizationMember member) {
        User user = member.getUser();
        return new ExaminerIdentityView(user.getPublicId(), user.getFullName(), user.getEmail());
    }
}
