package com.pte.identity.internal.repository;

import com.pte.identity.IdentityOverviewService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The platform-wide grouped student count behind {@link IdentityOverviewService#studentCountsByTenant()}: it must
 * count each live student once per tenant, ignore other roles, deleted and tenant-less users, and stay a single statement.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(IdentityOverviewService.class)
class IdentityPlatformOverviewQueriesTest {

    @Autowired
    private IdentityOverviewService service;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManagerFactory emf;

    private void user(UUID tenant, UserStatus status, boolean deleted, Role... roles) {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID());
        u.setEmail(u.getUsername() + "@example.test");
        u.setTenantId(tenant);
        u.setStatus(status);
        u.setDeleted(deleted);
        u.setRoles(new HashSet<>(Arrays.asList(roles)));
        users.saveAndFlush(u);
    }

    private void students(UUID tenant, int count) {
        for (int i = 0; i < count; i++) {
            user(tenant, UserStatus.ACTIVE, false, Role.STUDENT);
        }
    }

    @Test
    void countsLiveStudentsPerTenantWithoutMixingTenants() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        students(a, 3);
        students(b, 5);
        students(c, 1);

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThat(counts).containsEntry(a, 3L).containsEntry(b, 5L).containsEntry(c, 1L);
    }

    @Test
    void suspendedStudentsStillCountAsStudentsOfTheTenant() {
        UUID tenant = UUID.randomUUID();
        user(tenant, UserStatus.ACTIVE, false, Role.STUDENT);
        user(tenant, UserStatus.SUSPENDED, false, Role.STUDENT);

        assertThat(service.studentCountsByTenant()).containsEntry(tenant, 2L);
    }

    @Test
    void deletedUsersAndNonStudentRolesAreNeverCounted() {
        UUID tenant = UUID.randomUUID();
        students(tenant, 2);
        user(tenant, UserStatus.ACTIVE, true, Role.STUDENT); // soft-deleted
        user(tenant, UserStatus.ACTIVE, false, Role.HOST_ADMIN);
        user(tenant, UserStatus.ACTIVE, false, Role.PROCTOR);
        user(tenant, UserStatus.ACTIVE, false, Role.EXAMINER);

        assertThat(service.studentCountsByTenant()).containsEntry(tenant, 2L);
    }

    @Test
    void aUserWithSeveralRolesIsCountedOncePerTenantAsAStudent() {
        UUID tenant = UUID.randomUUID();
        user(tenant, UserStatus.ACTIVE, false, Role.STUDENT, Role.PROCTOR, Role.EXAMINER);
        user(tenant, UserStatus.ACTIVE, false, Role.STUDENT);

        assertThat(service.studentCountsByTenant()).containsEntry(tenant, 2L);
    }

    @Test
    void aTenantWhoseStudentsAreAllDeletedOrWhoseUsersAreNotStudentsIsAbsentFromTheMap() {
        UUID onlyDeleted = UUID.randomUUID();
        UUID onlyStaff = UUID.randomUUID();
        user(onlyDeleted, UserStatus.ACTIVE, true, Role.STUDENT);
        user(onlyStaff, UserStatus.ACTIVE, false, Role.HOST_ADMIN);

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThat(counts).doesNotContainKeys(onlyDeleted, onlyStaff);
    }

    @Test
    void platformUsersWithoutATenantAreExcluded() {
        UUID tenant = UUID.randomUUID();
        user(null, UserStatus.ACTIVE, false, Role.PLATFORM_ADMIN);
        user(null, UserStatus.ACTIVE, false, Role.STUDENT); // tenant-less student row must not appear under any key
        students(tenant, 1);

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThat(counts).containsEntry(tenant, 1L);
        assertThat(counts).doesNotContainKey(null);
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(1);
    }

    @Test
    void plainCountIsExactPerTenantForAMixedPopulation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        // tenant A: 2 pure students + 1 student who is also proctor and examiner (counted once) + 1 suspended student
        students(a, 2);
        user(a, UserStatus.ACTIVE, false, Role.STUDENT, Role.PROCTOR, Role.EXAMINER);
        user(a, UserStatus.SUSPENDED, false, Role.STUDENT);
        user(a, UserStatus.ACTIVE, false, Role.HOST_ADMIN, Role.PROCTOR); // not a student
        user(a, UserStatus.ACTIVE, true, Role.STUDENT, Role.PROCTOR); // deleted
        // tenant B: 1 student holding every tenant role, plus a deleted one
        user(b, UserStatus.ACTIVE, false, Role.STUDENT, Role.HOST_ADMIN, Role.PROCTOR, Role.EXAMINER);
        user(b, UserStatus.ACTIVE, true, Role.STUDENT);
        // tenant C: only staff
        user(c, UserStatus.ACTIVE, false, Role.PROCTOR);
        // tenant-less users never appear, whatever their roles
        user(null, UserStatus.ACTIVE, false, Role.STUDENT, Role.PLATFORM_ADMIN);
        user(null, UserStatus.ACTIVE, false, Role.PLATFORM_ADMIN);

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThat(counts).containsEntry(a, 4L).containsEntry(b, 1L).doesNotContainKey(c);
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(5L);
        assertThat(counts).doesNotContainKey(null);
    }

    @Test
    void noStudentsAtAllGivesAnEmptyMap() {
        user(UUID.randomUUID(), UserStatus.ACTIVE, false, Role.HOST_ADMIN);

        assertThat(service.studentCountsByTenant()).isEmpty();
    }

    @Test
    void theReturnedMapIsReadOnly() {
        students(UUID.randomUUID(), 1);

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThatThrownBy(() -> counts.put(UUID.randomUUID(), 1L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thePlatformWideCountIsOneGroupedStatementNotOnePerTenant() {
        for (int i = 0; i < 6; i++) {
            students(UUID.randomUUID(), 2);
        }
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Map<UUID, Long> counts = service.studentCountsByTenant();

        assertThat(counts).hasSize(6);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }
}
