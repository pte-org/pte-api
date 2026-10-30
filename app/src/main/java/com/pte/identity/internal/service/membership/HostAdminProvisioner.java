package com.pte.identity.internal.service.membership;

import com.pte.identity.IdentityService.HostContactNotAllowedException;
import com.pte.identity.domain.HostAdminCreated;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.internal.util.PasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

/** Makes an application contact the first HOST_ADMIN of an approved organization, reusing an existing account. */
public class HostAdminProvisioner {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public HostAdminProvisioner(UserRepository userRepository, OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public HostAdminCreated createHostAdmin(UUID tenantId, String email) {
        Optional<User> existing = userRepository.findByUsernameAndDeletedFalse(email);
        if (existing.isPresent()) {
            if (existing.get().hasPlatformRole()) {
                throw new HostContactNotAllowedException();
            }
            addMember(existing.get(), tenantId, Role.HOST_ADMIN);
            return new HostAdminCreated(existing.get(), null);
        }
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setStatus(UserStatus.ACTIVE);
        String password = PasswordGenerator.generateReadable();
        user.setCredentialHash(passwordEncoder.encode(password));
        User saved = userRepository.saveAndFlush(user);
        addMember(saved, tenantId, Role.HOST_ADMIN);
        return new HostAdminCreated(saved, password);
    }

    private void addMember(User user, UUID tenantId, Role role) {
        OrganizationMember member = memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(
                user.getId(), tenantId).orElseGet(OrganizationMember::new);
        member.setUser(user);
        member.setOrganizationId(tenantId);
        member.getRoles().add(role);
        memberRepository.saveAndFlush(member);
    }
}
