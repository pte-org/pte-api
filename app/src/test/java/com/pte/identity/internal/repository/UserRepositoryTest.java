package com.pte.identity.internal.repository;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

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
 * Phase 1 (only {@code username}, which the two rows below deliberately keep
 * distinct, still carries one). Actually generating distinct per-tenant
 * student usernames in production traffic is Phase 8's job, not this test's.
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
    void username_mustBeUniqueEvenAcrossTenants() {
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

        assertThat(userRepository.existsByUsername(sharedUsername)).isTrue();
        assertThatThrownBy(() -> userRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
