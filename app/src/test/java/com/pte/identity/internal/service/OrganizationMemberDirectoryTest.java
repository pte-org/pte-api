package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authorization rule: a Host manages only accounts of the organization in the token. An account of another
 * organization, a free practice student, a platform account or a removed account is reported as not found.
 */
@DataJpaTest
@Import(OrganizationMemberDirectory.class)
class OrganizationMemberDirectoryTest {

    @Autowired
    private OrganizationMemberDirectory directory;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aHostOfOrgACannotResolveAnAccountOfOrgB() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        User onlyInB = account("only-b@x.vn", orgB, Role.STUDENT);

        assertThatThrownBy(() -> directory.requireMember(orgA, onlyInB.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> directory.requireMemberForUpdate(orgA, onlyInB.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(directory.find(orgA, onlyInB.getPublicId())).isEmpty();
    }

    @Test
    void anAccountResolvesInsideItsOwnOrganizationWithAllItsRoles() {
        UUID org = UUID.randomUUID();
        User account = account("both@x.vn", org, Role.HOST_ADMIN, Role.EXAMINER);

        assertThat(directory.requireMember(org, account.getPublicId()).getRoles())
                .containsExactlyInAnyOrder(Role.HOST_ADMIN, Role.EXAMINER);
        assertThat(directory.requireMemberForUpdate(org, account.getPublicId())).isEqualTo(account);
    }

    @Test
    void aRemovedAccountNoLongerResolves() {
        UUID org = UUID.randomUUID();
        User user = account("left@x.vn", org, Role.PROCTOR);
        user.setDeleted(true);
        userRepository.flush();

        assertThatThrownBy(() -> directory.requireMember(org, user.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void aFreePracticeStudentWithoutAnOrganizationNeverResolvesInAnyOrganization() {
        User free = account("free@x.vn", null, Role.STUDENT);

        assertThatThrownBy(() -> directory.requireMember(UUID.randomUUID(), free.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> directory.requireMemberForUpdate(UUID.randomUUID(), free.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void aPlatformAccountNeverResolvesThroughAnOrganization() {
        User platform = account("root@x.vn", null, Role.PLATFORM_ADMIN);

        assertThat(directory.find(UUID.randomUUID(), platform.getPublicId())).isEmpty();
    }

    @Test
    void missingIdsResolveToNotFound() {
        assertThatThrownBy(() -> directory.requireMember(null, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> directory.requireMember(UUID.randomUUID(), null))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> directory.requireMemberForUpdate(null, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> directory.requireMemberForUpdate(UUID.randomUUID(), null))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User account(String username, UUID organizationId, Role... roles) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setCredentialHash("hash");
        user.setOrganizationId(organizationId);
        user.setRoles(new HashSet<>(Set.of(roles)));
        return userRepository.saveAndFlush(user);
    }
}
