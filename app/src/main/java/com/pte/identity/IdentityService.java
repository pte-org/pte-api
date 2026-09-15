package com.pte.identity;

import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code identity}. {@code
 * UserRepository} stays in {@code internal/} — a module reaching straight into
 * another module's repository is exactly the shortcut plan.md's FR-02 forbids.
 *
 * <p>Starts with just the three methods Phase 03 ({@code enrollment}) is known
 * to need. Add methods here as later phases need them; don't grow this
 * speculatively ahead of an actual caller.
 */
@Service
public class IdentityService {

    private final UserRepository userRepository;

    public IdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findById(UUID publicId) {
        return userRepository.findByPublicId(publicId);
    }

    public boolean existsById(UUID publicId) {
        return userRepository.findByPublicId(publicId).isPresent();
    }

    public Optional<UUID> getTenantOf(UUID publicId) {
        return userRepository.findByPublicId(publicId).map(User::getTenantId);
    }
}
