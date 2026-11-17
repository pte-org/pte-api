package com.pte.identity.internal.service.user;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.service.OrganizationMemberDirectory;
import com.pte.identity.internal.service.UserProvisioningHelper;
import com.pte.shared.security.CurrentUser;

import java.util.Set;
import java.util.UUID;

/** Resolves and authorizes the account a user-management call acts on. */
public class ManagedUserResolver {

    private final UserRepository userRepository;
    private final OrganizationMemberDirectory memberDirectory;
    private final UserProvisioningHelper provisioningHelper;

    public ManagedUserResolver(UserRepository userRepository, OrganizationMemberDirectory memberDirectory,
            UserProvisioningHelper provisioningHelper) {
        this.userRepository = userRepository;
        this.memberDirectory = memberDirectory;
        this.provisioningHelper = provisioningHelper;
    }

    /**
     * Resolves the target of a management call. A tenant caller reaches only accounts of its own organization; a
     * platform caller reaches any account and is judged by the roles the account holds.
     */
    public Target findScoped(UUID publicId, CurrentUser caller, boolean lock) {
        User user;
        if (caller.isPlatformUser()) {
            user = (lock ? userRepository.findWithLockByPublicId(publicId)
                    : userRepository.findByPublicIdAndDeletedFalse(publicId))
                    .orElseThrow(UserNotFoundException::new);
        } else {
            user = lock ? memberDirectory.requireMemberForUpdate(caller.tenantId(), publicId)
                    : memberDirectory.requireMember(caller.tenantId(), publicId);
        }
        Target target = new Target(user, Set.copyOf(user.getRoles()));
        provisioningHelper.authorizeTarget(caller, target.roles());
        return target;
    }

    /** The account a call acts on, with the roles it is judged by. */
    public record Target(User user, Set<Role> roles) {

        public UUID organizationId() {
            return user.getOrganizationId();
        }

        public UserResponse toResponse() {
            return UserMapper.toResponse(user);
        }

        public void suspend() {
            user.suspend();
        }

        public void reactivate() {
            if (user.isSuspended()) {
                user.reactivate();
            }
        }
    }
}
