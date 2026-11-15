package com.pte.identity.internal.service;

import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves an account only inside the caller's organization. An account of another organization, a platform account
 * or a removed account is reported as not found, never as forbidden, so ids never leak across organizations.
 */
@Service
public class OrganizationMemberDirectory {

    private final UserRepository userRepository;

    public OrganizationMemberDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireMember(UUID organizationId, UUID userPublicId) {
        return find(organizationId, userPublicId).orElseThrow(UserNotFoundException::new);
    }

    /** Same as {@link #requireMember} but holds a row lock for the caller's transaction. */
    @Transactional
    public User requireMemberForUpdate(UUID organizationId, UUID userPublicId) {
        if (organizationId == null || userPublicId == null) {
            throw new UserNotFoundException();
        }
        return userRepository.findWithLockByUserAndOrganization(userPublicId, organizationId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Optional<User> find(UUID organizationId, UUID userPublicId) {
        if (organizationId == null || userPublicId == null) {
            return Optional.empty();
        }
        return userRepository.findActiveByUserAndOrganization(userPublicId, organizationId);
    }
}
