package com.pte.itembank.internal.generation.worker;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rebuilds the caller a job runs as. The permission is checked again when the worker takes or resumes a job: an
 * admin who was suspended or demoted after submitting must not keep creating questions. The system actor is not
 * used because the questions must carry the requesting admin as author.
 */
@Component
public class GenerationCallerResolver {

    private final IdentityService identityService;

    public GenerationCallerResolver(IdentityService identityService) {
        this.identityService = identityService;
    }

    public Optional<CurrentUser> resolve(UUID requesterId) {
        return identityService.findById(requesterId)
                .filter(user -> !user.isDeleted())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles().contains(Role.PLATFORM_ADMIN))
                .map(user -> new CurrentUser(user.getPublicId(), null, List.of(SecurityRoles.PLATFORM_ADMIN)));
    }
}
