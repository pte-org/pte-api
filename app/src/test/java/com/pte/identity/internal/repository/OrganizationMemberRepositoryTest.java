package com.pte.identity.internal.repository;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 2: one User, several OrganizationMember rows (one per organization) carrying the tenant roles. */
@DataJpaTest
class OrganizationMemberRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Test
    void oneAccountCanBelongToTwoOrganizationsWithDifferentRoles() {
        User host = user("host@school.vn");
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        member(host, orgA, Role.HOST_ADMIN);
        member(host, orgB, Role.HOST_ADMIN, Role.EXAMINER);

        List<OrganizationMember> memberships = memberRepository.findByUser_IdAndDeletedFalse(host.getId());

        assertThat(memberships).extracting(OrganizationMember::getOrganizationId)
                .containsExactlyInAnyOrder(orgA, orgB);
        assertThat(memberRepository.findByUser_IdAndOrganizationIdAndDeletedFalse(host.getId(), orgB))
                .get().extracting(OrganizationMember::getRoles).isEqualTo(Set.of(Role.HOST_ADMIN, Role.EXAMINER));
    }

    @Test
    void anAccountCannotJoinTheSameOrganizationTwice() {
        User host = user("dup@school.vn");
        UUID org = UUID.randomUUID();
        member(host, org, Role.HOST_ADMIN);

        assertThatThrownBy(() -> member(host, org, Role.PROCTOR))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleCountsAreScopedToTheOrganizationAndIgnoreRemovedMembers() {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        member(user("s1@a.vn"), orgA, Role.STUDENT);
        member(user("s2@a.vn"), orgA, Role.STUDENT);
        OrganizationMember removed = member(user("s3@a.vn"), orgA, Role.STUDENT);
        removed.setDeleted(true);
        member(user("s4@b.vn"), orgB, Role.STUDENT);
        memberRepository.flush();

        assertThat(memberRepository.countByOrganizationIdAndRole(orgA, Role.STUDENT)).isEqualTo(2L);
        assertThat(memberRepository.countByOrganizationIdAndRole(orgB, Role.STUDENT)).isEqualTo(1L);
        assertThat(memberRepository.countByOrganizationIdAndRole(orgA, Role.HOST_ADMIN)).isZero();
    }

    @Test
    void usernamesAreUniqueAcrossTheWholePlatform() {
        user("same@school.vn");

        assertThatThrownBy(() -> user("same@school.vn")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String username) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Test " + username);
        user.setCredentialHash("hash");
        return userRepository.saveAndFlush(user);
    }

    private OrganizationMember member(User user, UUID organizationId, Role... roles) {
        OrganizationMember member = new OrganizationMember();
        member.setPublicId(UUID.randomUUID());
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setRoles(Set.of(roles));
        return memberRepository.saveAndFlush(member);
    }
}
