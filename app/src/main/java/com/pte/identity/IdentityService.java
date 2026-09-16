package com.pte.identity;

import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.tenancy.StudentCountProvider;
import com.pte.identity.internal.util.PasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The only door other modules use to reach {@code identity}. {@code
 * UserRepository} stays in {@code internal/} — a module reaching straight into
 * another module's repository is exactly the shortcut plan.md's FR-02 forbids.
 *
 * <p>Starts with just the three methods Phase 03 ({@code enrollment}) is known
 * to need, plus {@link #findByTenantIdAndRole} added for {@code notification}
 * (Phase 09) to fan out to every HOST_ADMIN in a tenant. Add methods here as
 * later phases need them; don't grow this speculatively ahead of an actual
 * caller.
 */
@Service
public class IdentityService implements StudentCountProvider {

    private final UserRepository userRepository;
    private final LoginHashRepository loginHashRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(UserRepository userRepository, LoginHashRepository loginHashRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.loginHashRepository = loginHashRepository;
        this.passwordEncoder = passwordEncoder;
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

    public List<User> findByTenantIdAndRole(UUID tenantId, Role role) {
        return userRepository.findByTenantIdAndRolesContaining(tenantId, role);
    }

    @Override
    public long countStudents(UUID tenantId) {
        return userRepository.countByTenantIdAndRole(tenantId, Role.STUDENT);
    }

    /**
     * {@code billing.TenantApplicationService.approve()} — creates the first
     * HOST_ADMIN of a newly approved tenant. No {@code @Transactional} of its
     * own for the same reason as {@code TenantLifecycleService.createFromApplication}:
     * this must join the caller's transaction, not commit independently.
     */
    public HostAdminCreated createHostAdmin(UUID tenantId, String email) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.HOST_ADMIN));
        User saved = userRepository.saveAndFlush(user);

        String password = PasswordGenerator.generateReadable();
        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(saved.getId());
        loginHash.setHash(passwordEncoder.encode(password));
        loginHashRepository.save(loginHash);

        return new HostAdminCreated(saved, password);
    }
}
