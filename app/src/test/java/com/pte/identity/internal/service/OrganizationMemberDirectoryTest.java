package com.pte.identity.internal.service;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 authorization rule: a Host manages only users who are members of the organization in the token.
 * A person who belongs to two organizations is invisible to the other organization's Host once the
 * membership in that organization is removed, and never readable through an organization they never joined.
 */
@DataJpaTest
@Import(OrganizationMemberDirectory.class)
class OrganizationMemberDirectoryTest {

    @Autowired
    private OrganizationMemberDirectory directory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Test
    void aHostOfOrgACannotResolveAUserWhoOnlyBelongsToOrgB() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        User onlyInB = user("only-b@x.vn");
        join(onlyInB, orgB, Role.STUDENT);

        assertThatThrownBy(() -> directory.requireMember(orgA, onlyInB.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void aUserInBothOrganizationsResolvesThroughEachMembershipWithItsOwnRoles() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        User shared = user("shared@x.vn");
        join(shared, orgA, Role.HOST_ADMIN);
        join(shared, orgB, Role.STUDENT);

        assertThat(directory.requireMember(orgA, shared.getPublicId()).getRoles()).containsExactly(Role.HOST_ADMIN);
        assertThat(directory.requireMember(orgB, shared.getPublicId()).getRoles()).containsExactly(Role.STUDENT);
    }

    @Test
    void aRemovedMembershipNoLongerResolves() {
        UUID org = UUID.randomUUID();
        User user = user("left@x.vn");
        OrganizationMember member = join(user, org, Role.PROCTOR);
        member.setDeleted(true);
        memberRepository.flush();

        assertThatThrownBy(() -> directory.requireMember(org, user.getPublicId()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void anAccountOnlyThisOrganizationKnowsIsExclusivelyOwned() {
        UUID org = UUID.randomUUID();
        OrganizationMember member = join(user("own@x.vn"), org, Role.STUDENT);

        assertThat(directory.isExclusivelyOwned(member)).isTrue();
    }

    @Test
    void anAccountWithAnotherMembershipIsNotExclusivelyOwned() {
        User shared = user("two@x.vn");
        OrganizationMember inA = join(shared, UUID.randomUUID(), Role.STUDENT);
        join(shared, UUID.randomUUID(), Role.STUDENT);

        assertThat(directory.isExclusivelyOwned(inA)).isFalse();
    }

    @Test
    void anAccountWithRolesOfItsOwnIsNotExclusivelyOwned() {
        User practice = user("practice@x.vn");
        practice.getRoles().add(Role.STUDENT);
        userRepository.saveAndFlush(practice);
        OrganizationMember member = join(practice, UUID.randomUUID(), Role.STUDENT);

        assertThat(directory.isExclusivelyOwned(member)).isFalse();
    }

    private User user(String username) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setCredentialHash("hash");
        return userRepository.saveAndFlush(user);
    }

    private OrganizationMember join(User user, UUID organizationId, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setPublicId(UUID.randomUUID());
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setRoles(Set.of(role));
        return memberRepository.saveAndFlush(member);
    }
}
