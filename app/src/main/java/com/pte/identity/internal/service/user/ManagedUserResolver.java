package com.pte.identity.internal.service.user;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.OrganizationMemberDirectory;
import com.pte.identity.internal.service.UserProvisioningHelper;
import com.pte.shared.security.CurrentUser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolves and authorizes the account a user-management call acts on. */
public class ManagedUserResolver {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationMemberDirectory memberDirectory;
    private final UserProvisioningHelper provisioningHelper;

    public ManagedUserResolver(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            OrganizationMemberDirectory memberDirectory, UserProvisioningHelper provisioningHelper) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.memberDirectory = memberDirectory;
        this.provisioningHelper = provisioningHelper;
    }

    /**
     * Resolves the target of a management call. A tenant caller reaches only members of its own organization; a
     * platform caller reaches any account and is judged by every role the account holds.
     */
    public Target findScoped(UUID publicId, CurrentUser caller, boolean lock) {
        Target target;
        if (caller.isPlatformUser()) {
            User user = (lock ? userRepository.findWithLockByPublicId(publicId)
                    : userRepository.findByPublicIdAndDeletedFalse(publicId))
                    .orElseThrow(UserNotFoundException::new);
            target = Target.ofAccount(user, memberRepository.findByUser_IdAndDeletedFalse(user.getId()));
        } else {
            OrganizationMember member = lock ? memberDirectory.requireMemberForUpdate(caller.tenantId(), publicId)
                    : memberDirectory.requireMember(caller.tenantId(), publicId);
            target = Target.ofMember(member);
        }
        provisioningHelper.authorizeTarget(caller, target.roles());
        return target;
    }

    /** The account a call acts on, with the roles it is judged by and the membership (if one organization). */
    public record Target(User user, OrganizationMember member, Set<Role> roles) {

        static Target ofMember(OrganizationMember member) {
            return new Target(member.getUser(), member, Set.copyOf(member.getRoles()));
        }

        static Target ofAccount(User user, List<OrganizationMember> members) {
            Set<Role> roles = new HashSet<>(user.getRoles());
            members.forEach(member -> roles.addAll(member.getRoles()));
            return new Target(user, members.size() == 1 ? members.get(0) : null, Set.copyOf(roles));
        }

        public UUID organizationId() {
            return member == null ? null : member.getOrganizationId();
        }

        public UserResponse toResponse() {
            return UserMapper.toResponse(user, member, null);
        }

        /** Suspension is per organization for a Host, and account-wide for a platform operator. */
        public void suspend() {
            if (member != null) {
                member.suspend();
            } else {
                user.suspend();
            }
        }

        public void reactivate() {
            if (member != null) {
                if (member.getStatus() == com.pte.identity.domain.UserStatus.SUSPENDED) {
                    member.reactivate();
                }
            } else if (user.isSuspended()) {
                user.reactivate();
            }
        }
    }
}
