package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.internal.repository.TenantRepository;
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
 * {@code @DataJpaTest} against the real (H2-derived) schema — proves the
 * schema change itself, not any service-layer behavior: two different
 * tenants can each have a student with the same email, because {@code email}
 * dropped its unique constraint in plans/quang-tenant-commercialization
 * Phase 1. Usernames are unique inside a tenant but may be reused by a
 * different tenant so duplicate host-admin emails can be disambiguated at
 * login time.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Tenant persistTenant(String code) {
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName("Tenant " + code);
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        return tenantRepository.save(tenant);
    }

    @Test
    void twoDifferentTenants_canEachHaveAStudentWithTheSameEmail() {
        Tenant tenantA = persistTenant("tenant-a-" + UUID.randomUUID());
        Tenant tenantB = persistTenant("tenant-b-" + UUID.randomUUID());
        String sharedEmail = "shared.student@example.test";

        User studentA = new User();
        studentA.setUsername(tenantA.getCode() + ".aaaaaaaa");
        studentA.setEmail(sharedEmail);
        studentA.setTenantId(tenantA.getPublicId());
        studentA.setRoles(Set.of(Role.STUDENT));
        userRepository.saveAndFlush(studentA);

        User studentB = new User();
        studentB.setUsername(tenantB.getCode() + ".bbbbbbbb");
        studentB.setEmail(sharedEmail);
        studentB.setTenantId(tenantB.getPublicId());
        studentB.setRoles(Set.of(Role.STUDENT));
        userRepository.saveAndFlush(studentB);

        List<User> withSharedEmail = userRepository.findByEmailIn(List.of(sharedEmail));
        assertThat(withSharedEmail).hasSize(2);
        assertThat(withSharedEmail).extracting(User::getTenantId)
                .containsExactlyInAnyOrder(tenantA.getPublicId(), tenantB.getPublicId());
    }

    @Test
    void username_mayBeReusedAcrossDifferentTenants() {
        Tenant tenantA = persistTenant("tenant-c-" + UUID.randomUUID());
        Tenant tenantB = persistTenant("tenant-d-" + UUID.randomUUID());
        String sharedUsername = "collision@example.test";

        User first = new User();
        first.setUsername(sharedUsername);
        first.setEmail(sharedUsername);
        first.setTenantId(tenantA.getPublicId());
        first.setRoles(Set.of(Role.HOST_ADMIN));
        userRepository.saveAndFlush(first);

        User second = new User();
        second.setUsername(sharedUsername);
        second.setEmail("different@example.test");
        second.setTenantId(tenantB.getPublicId());
        second.setRoles(Set.of(Role.HOST_ADMIN));

        assertThat(userRepository.existsByUsernameAndTenantId(sharedUsername, tenantA.getPublicId())).isTrue();
        userRepository.saveAndFlush(second);
    }

    @Test
    void username_mustBeUniqueInsideTheSameTenant() {
        Tenant tenant = persistTenant("tenant-e-" + UUID.randomUUID());
        String sharedUsername = "collision@example.test";

        User first = new User();
        first.setUsername(sharedUsername);
        first.setEmail(sharedUsername);
        first.setTenantId(tenant.getPublicId());
        first.setRoles(Set.of(Role.HOST_ADMIN));
        userRepository.saveAndFlush(first);

        User second = new User();
        second.setUsername(sharedUsername);
        second.setEmail("different@example.test");
        second.setTenantId(tenant.getPublicId());
        second.setRoles(Set.of(Role.HOST_ADMIN));

        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void examStaffPage_filtersStaffRolesSearchAndStatus() {
        Tenant tenant = persistTenant("tenant-staff-" + UUID.randomUUID());

        User examiner = new User();
        examiner.setUsername("examiner@example.test");
        examiner.setEmail("examiner@example.test");
        examiner.setFullName("Examiner One");
        examiner.setTenantId(tenant.getPublicId());
        examiner.setStatus(UserStatus.ACTIVE);
        examiner.setRoles(Set.of(Role.EXAMINER));
        userRepository.save(examiner);

        User student = new User();
        student.setUsername("student@example.test");
        student.setEmail("student@example.test");
        student.setFullName("Student One");
        student.setTenantId(tenant.getPublicId());
        student.setRoles(Set.of(Role.STUDENT));
        userRepository.saveAndFlush(student);

        Page<User> result = userRepository.findPageForExamStaff(
                tenant.getPublicId(), Set.of(Role.PROCTOR, Role.EXAMINER), Role.EXAMINER,
                UserStatus.ACTIVE, "examiner", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(User::getUsername)
                .containsExactly("examiner@example.test");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void examinerAssignmentQueryLocksOnlyRequestedTenantUsersInStableOrder() {
        Tenant tenant = persistTenant("tenant-lock-" + UUID.randomUUID());
        User examinerA = createUser(tenant, "examiner-a-" + UUID.randomUUID(), Role.EXAMINER);
        User examinerB = createUser(tenant, "examiner-b-" + UUID.randomUUID(), Role.EXAMINER);
        Tenant otherTenant = persistTenant("tenant-lock-other-" + UUID.randomUUID());
        User otherExaminer = createUser(otherTenant, "examiner-other-" + UUID.randomUUID(), Role.EXAMINER);

        List<User> locked = userRepository.findWithLockByPublicIdsAndTenantId(
                List.of(examinerB.getPublicId(), otherExaminer.getPublicId(), examinerA.getPublicId()),
                tenant.getPublicId());

        assertThat(locked).extracting(User::getPublicId)
                .containsExactly(examinerA.getPublicId(), examinerB.getPublicId());
    }

    private User createUser(Tenant tenant, String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName(username);
        user.setTenantId(tenant.getPublicId());
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }
}
