package com.pte.scoring.internal.rubric.service;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** The full name of a user, for the lists that say who made or changed a rubric version; unknown when not found. */
@Component
public class RubricUserNames {

    private final IdentityService identity;

    public RubricUserNames(IdentityService identity) {
        this.identity = identity;
    }

    public Optional<String> nameOf(UUID userPublicId) {
        return userPublicId == null ? Optional.empty() : identity.findById(userPublicId).map(User::getFullName);
    }
}
