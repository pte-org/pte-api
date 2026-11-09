package com.pte.itembank.internal.generation.worker;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
import com.pte.itembank.internal.generation.job.GenerationAccessPolicy;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rebuilds the caller a job runs as, with the writer roles the requester holds now. The permission is checked again
 * when the worker takes or resumes a job: a writer who was suspended or lost the role after submitting must not keep
 * creating questions, and a staff writer's questions are saved with staff rights, never more. The system actor is
 * not used because the questions must carry the requester as author.
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
                .map(user -> new CurrentUser(user.getPublicId(), null, writerRoles(user.getRoles())))
                .filter(caller -> !caller.roles().isEmpty());
    }

    private static List<String> writerRoles(Collection<Role> roles) {
        return roles.stream().map(Role::name).filter(GenerationAccessPolicy.AUTHOR_ROLES::contains).toList();
    }
}
