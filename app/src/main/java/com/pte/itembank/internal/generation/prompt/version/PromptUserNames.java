package com.pte.itembank.internal.generation.prompt.version;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The full name of a user, for the lists that say who wrote, tested or approved a prompt; null when not found. */
@Component
class PromptUserNames {

    private final IdentityService identity;

    PromptUserNames(IdentityService identity) {
        this.identity = identity;
    }

    String nameOf(UUID userPublicId) {
        return userPublicId == null ? null : identity.findById(userPublicId).map(User::getFullName).orElse(null);
    }
}
