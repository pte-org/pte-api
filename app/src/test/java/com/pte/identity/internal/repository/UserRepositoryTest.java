package com.pte.identity.internal.repository;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @DataJpaTest} against the real (H2-derived) schema. An account is global: the username is unique across
 * the platform, while e-mail is not, and per-organization roles/status live on {@link OrganizationMember}.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    private User persistUser(String username, String email, String fullName, Set<Role> roles) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private OrganizationMember join(User user, UUID organizationId, Role role) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(organizationId);
        member.setStatus(UserStatus.ACTIVE);
        member.setRoles(Set.of(role));
        return memberRepository.saveAndFlush(member);
    }

    @Test
    void twoAccounts_canShareTheSameEmail() {
        String sharedEmail = "shared.student@example.test";
        persistUser("org-a.aaaaaaaa", sharedEmail, null, Set.of());
        persistUser("org-b.bbbbbbbb", sharedEmail, null, Set.of());

        assertThat(userRepository.findByEmailIgnoreCaseAndDeletedFalse(sharedEmail)).hasSize(2);
    }

    @Test
    void username_mustBeUniqueAcrossThePlatform() {
        String username = "collision@example.test";
        persistUser(username, username, null, Set.of());

        assertThat(userRepository.existsByUsername(username)).isTrue();
        assertThatThrownBy(() -> persistUser(username, "different@example.test", null, Set.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void examStaffPage_filtersStaffRolesSearchAndStatusInsideTheOrganization() {
        UUID organizationId = UUID.randomUUID();
        User examiner = persistUser("examiner@example.test", "examiner@example.test", "Examiner One", Set.of());
        User student = persistUser("student@example.test", "student@example.test", "Student One", Set.of());
        join(examiner, organizationId, Role.EXAMINER);
        join(student, organizationId, Role.STUDENT);

        Page<OrganizationMember> result = memberRepository.findPageForExamStaff(
                organizationId, Set.of(Role.PROCTOR, Role.EXAMINER), Role.EXAMINER,
                UserStatus.ACTIVE, "examiner", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(member -> member.getUser().getUsername())
                .containsExactly("examiner@example.test");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void examinerAssignmentQueryLocksOnlyRequestedOrganizationMembersInStableOrder() {
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        User examinerA = createUser("examiner-a-" + UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        User examinerB = createUser("examiner-b-" + UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        User otherExaminer = createUser("examiner-other-" + UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000003"));
        join(examinerA, organizationId, Role.EXAMINER);
        join(examinerB, organizationId, Role.EXAMINER);
        join(otherExaminer, otherOrganizationId, Role.EXAMINER);

        List<OrganizationMember> locked = memberRepository.findWithLockByUsersAndOrganization(
                List.of(examinerB.getPublicId(), otherExaminer.getPublicId(), examinerA.getPublicId()),
                organizationId);

        assertThat(locked).extracting(member -> member.getUser().getPublicId())
                .containsExactly(examinerA.getPublicId(), examinerB.getPublicId());
    }

    private User createUser(String username, UUID publicId) {
        User user = new User();
        user.setPublicId(publicId);
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName(username);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
