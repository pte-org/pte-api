package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The organization-scoped queries of {@link UserRepository}. An account carries one organization; every query that
 * takes an organization must never see an account of another organization, a free practice student (no organization),
 * a platform account or an archived account. Each method has a negative cross-tenant case.
 */
@DataJpaTest
class UserRepositoryOrganizationScopeTest {

    private static final Set<Role> STAFF = Set.of(Role.PROCTOR, Role.EXAMINER);

    @Autowired
    private UserRepository userRepository;

    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void organizations() {
        orgA = UUID.randomUUID();
        orgB = UUID.randomUUID();
    }

    private User account(UUID organizationId, String email, UserStatus status, boolean deleted, Role... roles) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("u-" + UUID.randomUUID());
        user.setEmail(email);
        user.setFullName("Name " + email);
        user.setCredentialHash("hash");
        user.setStatus(status);
        user.setDeleted(deleted);
        user.setOrganizationId(organizationId);
        user.setRoles(new HashSet<>(Arrays.asList(roles)));
        return userRepository.saveAndFlush(user);
    }

    private User account(UUID organizationId, String email, Role... roles) {
        return account(organizationId, email, UserStatus.ACTIVE, false, roles);
    }

    // ---- by-id reads ----

    @Test
    void findActiveByUserAndOrganization_findsOwnAccountOnly() {
        User inA = account(orgA, "a@a.vn", Role.STUDENT);
        User inB = account(orgB, "b@b.vn", Role.STUDENT);

        assertThat(userRepository.findActiveByUserAndOrganization(inA.getPublicId(), orgA)).contains(inA);
        assertThat(userRepository.findActiveByUserAndOrganization(inB.getPublicId(), orgA)).isEmpty();
        assertThat(userRepository.findActiveByUserAndOrganization(inA.getPublicId(), orgB)).isEmpty();
    }

    @Test
    void findActiveByUserAndOrganization_neverReturnsFreeStudentPlatformAccountOrArchivedAccount() {
        User free = account(null, "free@x.vn", Role.STUDENT);
        User platform = account(null, "root@x.vn", Role.PLATFORM_ADMIN);
        User archived = account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.STUDENT);

        assertThat(userRepository.findActiveByUserAndOrganization(free.getPublicId(), orgA)).isEmpty();
        assertThat(userRepository.findActiveByUserAndOrganization(platform.getPublicId(), orgA)).isEmpty();
        assertThat(userRepository.findActiveByUserAndOrganization(archived.getPublicId(), orgA)).isEmpty();
    }

    @Test
    void findWithLockByUserAndOrganization_locksOwnAccountOnly() {
        User inA = account(orgA, "a@a.vn", Role.PROCTOR);
        User inB = account(orgB, "b@b.vn", Role.PROCTOR);
        User free = account(null, "free@x.vn", Role.STUDENT);
        User archived = account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.PROCTOR);

        assertThat(userRepository.findWithLockByUserAndOrganization(inA.getPublicId(), orgA)).contains(inA);
        assertThat(userRepository.findWithLockByUserAndOrganization(inB.getPublicId(), orgA)).isEmpty();
        assertThat(userRepository.findWithLockByUserAndOrganization(free.getPublicId(), orgA)).isEmpty();
        assertThat(userRepository.findWithLockByUserAndOrganization(archived.getPublicId(), orgA)).isEmpty();
    }

    @Test
    void findActiveByUsersAndOrganization_skipsAccountsOfOtherOrganizationsAndFreeStudents() {
        User inA = account(orgA, "a@a.vn", Role.STUDENT);
        User inB = account(orgB, "b@b.vn", Role.STUDENT);
        User free = account(null, "free@x.vn", Role.STUDENT);
        User archived = account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.STUDENT);

        List<User> found = userRepository.findActiveByUsersAndOrganization(
                List.of(inA.getPublicId(), inB.getPublicId(), free.getPublicId(), archived.getPublicId()), orgA);

        assertThat(found).containsExactly(inA);
        assertThat(userRepository.findActiveByUsersAndOrganization(List.of(inA.getPublicId()), orgB)).isEmpty();
    }

    @Test
    void findWithLockByUsersAndOrganization_locksOnlyOwnAccountsInPublicIdOrder() {
        User first = fixedIdAccount("00000000-0000-0000-0000-000000000011", orgA, "x1@a.vn");
        User second = fixedIdAccount("00000000-0000-0000-0000-000000000012", orgA, "x2@a.vn");
        User otherOrg = fixedIdAccount("00000000-0000-0000-0000-000000000013", orgB, "x3@b.vn");
        User free = fixedIdAccount("00000000-0000-0000-0000-000000000014", null, "x4@x.vn");

        List<User> locked = userRepository.findWithLockByUsersAndOrganization(
                List.of(otherOrg.getPublicId(), second.getPublicId(), free.getPublicId(), first.getPublicId()), orgA);

        assertThat(locked).extracting(User::getPublicId).containsExactly(first.getPublicId(), second.getPublicId());
    }

    // ---- organization listings ----

    @Test
    void findActiveByOrganization_listsOnlyLiveAccountsOfThatOrganization() {
        User inA = account(orgA, "a@a.vn", Role.STUDENT);
        User suspendedInA = account(orgA, "s@a.vn", UserStatus.SUSPENDED, false, Role.STUDENT);
        account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.STUDENT);
        account(orgB, "b@b.vn", Role.STUDENT);
        account(null, "free@x.vn", Role.STUDENT);

        assertThat(userRepository.findActiveByOrganization(orgA)).containsExactlyInAnyOrder(inA, suspendedInA);
        assertThat(userRepository.findActiveByOrganization(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findActiveByOrganizationAndEmails_matchesCaseAndPaddingInsideTheOrganizationOnly() {
        User inA = account(orgA, "  Same@Mail.Example ", Role.STUDENT);
        account(orgB, "same@mail.example", Role.STUDENT);
        account(null, "same@mail.example", Role.STUDENT);
        account(orgA, "gone@mail.example", UserStatus.ACTIVE, true, Role.STUDENT);

        assertThat(userRepository.findActiveByOrganizationAndEmails(orgA,
                Set.of("same@mail.example", "gone@mail.example"))).containsExactly(inA);
        assertThat(userRepository.findActiveByOrganizationAndEmails(UUID.randomUUID(),
                Set.of("same@mail.example"))).isEmpty();
    }

    @Test
    void existsCanonicalEmail_isPerOrganization_andIgnoresTheExcludedAccount() {
        User inA = account(orgA, "Taken@Mail.Example", Role.STUDENT);
        UUID someoneElse = UUID.randomUUID();

        assertThat(userRepository.existsCanonicalEmail(orgA, "taken@mail.example", someoneElse)).isTrue();
        assertThat(userRepository.existsCanonicalEmail(orgA, "taken@mail.example", inA.getPublicId())).isFalse();
        // the same e-mail in another organization is a different account and does not clash
        assertThat(userRepository.existsCanonicalEmail(orgB, "taken@mail.example", someoneElse)).isFalse();
    }

    @Test
    void existsCanonicalEmail_ignoresArchivedAndFreeStudents() {
        account(orgA, "gone@mail.example", UserStatus.ACTIVE, true, Role.STUDENT);
        account(null, "free@mail.example", Role.STUDENT);

        assertThat(userRepository.existsCanonicalEmail(orgA, "gone@mail.example", UUID.randomUUID())).isFalse();
        assertThat(userRepository.existsCanonicalEmail(orgA, "free@mail.example", UUID.randomUUID())).isFalse();
    }

    // ---- counts ----

    @Test
    void countByOrganizationIdAndRole_countsOnlyLiveAccountsOfTheOrganizationWithTheRole() {
        account(orgA, "s1@a.vn", Role.STUDENT);
        account(orgA, "s2@a.vn", Role.STUDENT, Role.PROCTOR); // multi-role: once
        account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.STUDENT);
        account(orgA, "h@a.vn", Role.HOST_ADMIN);
        account(orgB, "s4@b.vn", Role.STUDENT);
        account(null, "free@x.vn", Role.STUDENT);

        assertThat(userRepository.countByOrganizationIdAndRole(orgA, Role.STUDENT)).isEqualTo(2L);
        assertThat(userRepository.countByOrganizationIdAndRole(orgB, Role.STUDENT)).isEqualTo(1L);
        assertThat(userRepository.countByOrganizationIdAndRole(orgA, Role.EXAMINER)).isZero();
        assertThat(userRepository.countByOrganizationIdAndRole(UUID.randomUUID(), Role.STUDENT)).isZero();
    }

    @Test
    void countByOrganizationAndRoleGroupedByStatus_isScopedToTheOrganization() {
        account(orgA, "a1@a.vn", Role.STUDENT);
        account(orgA, "a2@a.vn", UserStatus.SUSPENDED, false, Role.STUDENT);
        account(orgB, "b1@b.vn", Role.STUDENT);
        account(orgB, "b2@b.vn", Role.STUDENT);
        account(null, "free@x.vn", Role.STUDENT);

        Map<UserStatus, Long> perStatusA = byStatus(userRepository.countByOrganizationAndRoleGroupedByStatus(
                orgA, Role.STUDENT));
        Map<UserStatus, Long> perStatusB = byStatus(userRepository.countByOrganizationAndRoleGroupedByStatus(
                orgB, Role.STUDENT));

        assertThat(perStatusA).containsEntry(UserStatus.ACTIVE, 1L).containsEntry(UserStatus.SUSPENDED, 1L);
        assertThat(perStatusB).containsEntry(UserStatus.ACTIVE, 2L).doesNotContainKey(UserStatus.SUSPENDED);
        assertThat(userRepository.countByOrganizationAndRoleGroupedByStatus(UUID.randomUUID(), Role.STUDENT))
                .isEmpty();
    }

    @Test
    void countByOrganizationAndRoleCreatedBetween_neverCountsAnotherOrganization() {
        account(orgA, "a1@a.vn", Role.STUDENT);
        account(orgB, "b1@b.vn", Role.STUDENT);
        account(orgB, "b2@b.vn", Role.STUDENT);
        account(null, "free@x.vn", Role.STUDENT);
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThat(userRepository.countByOrganizationAndRoleCreatedBetween(orgA, Role.STUDENT, from, to))
                .isEqualTo(1L);
        assertThat(userRepository.countByOrganizationAndRoleCreatedBetween(orgB, Role.STUDENT, from, to))
                .isEqualTo(2L);
        assertThat(userRepository.countByOrganizationAndRoleCreatedBetween(UUID.randomUUID(), Role.STUDENT, from, to))
                .isZero();
    }

    @Test
    void countActiveByOrganizationAndRoles_countsOnlyActiveStaffOfTheOrganization() {
        account(orgA, "p@a.vn", Role.PROCTOR);
        account(orgA, "e@a.vn", Role.EXAMINER);
        account(orgA, "sp@a.vn", UserStatus.SUSPENDED, false, Role.PROCTOR);
        account(orgB, "p1@b.vn", Role.PROCTOR);
        account(orgB, "p2@b.vn", Role.PROCTOR);

        Map<Role, Long> staffA = byRole(userRepository.countActiveByOrganizationAndRoles(
                orgA, STAFF, UserStatus.ACTIVE));
        Map<Role, Long> staffB = byRole(userRepository.countActiveByOrganizationAndRoles(
                orgB, STAFF, UserStatus.ACTIVE));

        assertThat(staffA).containsEntry(Role.PROCTOR, 1L).containsEntry(Role.EXAMINER, 1L);
        assertThat(staffB).containsEntry(Role.PROCTOR, 2L).doesNotContainKey(Role.EXAMINER);
        assertThat(userRepository.countActiveByOrganizationAndRoles(UUID.randomUUID(), STAFF, UserStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void countByRoleGroupedByOrganization_leavesOutFreeStudentsAndArchivedAccounts() {
        account(orgA, "a1@a.vn", Role.STUDENT);
        account(orgA, "a2@a.vn", Role.STUDENT);
        account(orgB, "b1@b.vn", Role.STUDENT);
        account(orgB, "gone@b.vn", UserStatus.ACTIVE, true, Role.STUDENT);
        account(null, "free1@x.vn", Role.STUDENT);
        account(null, "free2@x.vn", Role.STUDENT);

        Map<UUID, Long> perOrganization = userRepository.countByRoleGroupedByOrganization(Role.STUDENT).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        assertThat(perOrganization).containsEntry(orgA, 2L).containsEntry(orgB, 1L).doesNotContainKey(null);
    }

    // ---- role fan-out ----

    @Test
    void findActiveByOrganizationAndRole_returnsOnlyOwnLiveAccountsWithTheRole() {
        User hostA = account(orgA, "h@a.vn", Role.HOST_ADMIN);
        account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.HOST_ADMIN);
        account(orgA, "s@a.vn", Role.STUDENT);
        account(orgB, "h@b.vn", Role.HOST_ADMIN);

        assertThat(userRepository.findActiveByOrganizationAndRole(orgA, Role.HOST_ADMIN)).containsExactly(hostA);
        assertThat(userRepository.findActiveByOrganizationAndRole(UUID.randomUUID(), Role.HOST_ADMIN)).isEmpty();
    }

    @Test
    void findActiveByRole_spansOrganizationsButNeverReturnsFreeStudentsSuspendedOrArchivedAccounts() {
        User inA = account(orgA, "a@a.vn", Role.STUDENT);
        User inB = account(orgB, "b@b.vn", Role.STUDENT);
        User free = account(null, "free@x.vn", Role.STUDENT);
        User suspended = account(orgA, "s@a.vn", UserStatus.SUSPENDED, false, Role.STUDENT);
        User archived = account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.STUDENT);

        List<User> found = userRepository.findActiveByRole(Role.STUDENT, UserStatus.ACTIVE);

        assertThat(found).contains(inA, inB).doesNotContain(free, suspended, archived);
    }

    // ---- staff directory page ----

    @Test
    void findPageForExamStaff_neverListsAnotherOrganizationsStaffOrFreeStudents() {
        User examinerA = account(orgA, "ea@a.vn", Role.EXAMINER);
        account(orgB, "eb@b.vn", Role.EXAMINER);
        account(orgA, "gone@a.vn", UserStatus.ACTIVE, true, Role.EXAMINER);
        account(orgA, "s@a.vn", Role.STUDENT);
        account(null, "free@x.vn", Role.STUDENT);

        Page<User> pageA = userRepository.findPageForExamStaff(orgA, STAFF, null, null, "", PageRequest.of(0, 20));
        Page<User> pageB = userRepository.findPageForExamStaff(orgB, STAFF, null, null, "", PageRequest.of(0, 20));
        Page<User> none = userRepository.findPageForExamStaff(UUID.randomUUID(), STAFF, null, null, "",
                PageRequest.of(0, 20));

        assertThat(pageA.getContent()).containsExactly(examinerA);
        assertThat(pageA.getTotalElements()).isEqualTo(1);
        assertThat(pageB.getContent()).extracting(User::getEmail).containsExactly("eb@b.vn");
        assertThat(none.getContent()).isEmpty();
    }

    // ---- constraints ----

    @Test
    void username_staysUniqueAcrossOrganizations() {
        User first = account(orgA, "a@a.vn", Role.STUDENT);

        assertThatThrownBy(() -> {
            User clash = new User();
            clash.setUsername(first.getUsername());
            clash.setOrganizationId(orgB);
            userRepository.saveAndFlush(clash);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAccountCarriesItsOrganizationAndAllItsRoles() {
        User saved = account(orgA, "multi@a.vn", Role.HOST_ADMIN, Role.EXAMINER);

        User reloaded = userRepository.findByPublicId(saved.getPublicId()).orElseThrow();

        assertThat(reloaded.getOrganizationId()).isEqualTo(orgA);
        assertThat(reloaded.getRoles()).containsExactlyInAnyOrder(Role.HOST_ADMIN, Role.EXAMINER);
    }

    private User fixedIdAccount(String publicId, UUID organizationId, String email) {
        User user = new User();
        user.setPublicId(UUID.fromString(publicId));
        user.setUsername("u-" + UUID.randomUUID());
        user.setEmail(email);
        user.setOrganizationId(organizationId);
        user.setRoles(new HashSet<>(Set.of(Role.STUDENT)));
        return userRepository.saveAndFlush(user);
    }

    private static Map<UserStatus, Long> byStatus(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UserStatus) row[0], row -> (Long) row[1]));
    }

    private static Map<Role, Long> byRole(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Role) row[0], row -> (Long) row[1]));
    }
}
