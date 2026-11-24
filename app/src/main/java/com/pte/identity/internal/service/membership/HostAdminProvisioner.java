package com.pte.identity.internal.service.membership;

import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import com.pte.identity.internal.util.UsernameGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/**
 * Makes an application contact the first HOST_ADMIN of an approved organization. The Host is always a new account of
 * that organization with a generated username, so the same e-mail can also be a Host or staff of another one.
 */
public class HostAdminProvisioner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public HostAdminProvisioner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public HostAdminCreated createHostAdmin(UUID tenantId, String tenantCode, String email) {
        User user = new User();
        user.setUsername(UsernameGenerator.generateUnused(tenantCode, email, userRepository::existsByUsername));
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        user.setOrganizationId(tenantId);
        user.getRoles().add(Role.HOST_ADMIN);
        String password = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(password));
        // The generated password is mailed in clear text, so the Host must replace it at first sign-in.
        user.setMustChangePassword(true);
        return new HostAdminCreated(userRepository.saveAndFlush(user), password);
    }
}
