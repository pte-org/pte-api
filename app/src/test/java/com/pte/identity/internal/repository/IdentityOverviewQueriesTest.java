package com.pte.identity.internal.repository;

import com.pte.identity.IdentityOverviewService;
import com.pte.identity.IdentityOverviewService.StaffCounts;
import com.pte.identity.IdentityOverviewService.StudentStatusCounts;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every identity query behind {@link IdentityOverviewService} against the embedded
 * database: tenant isolation, soft-delete filtering, role filtering and half-open
 * time ranges. Tenant ids here are plain UUIDs (identity stores no FK to tenancy).
 */
@DataJpaTest
@Import(IdentityOverviewService.class)
class IdentityOverviewQueriesTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private IdentityOverviewService service;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager em;

    private User user(UUID tenant, UserStatus status, boolean deleted, Instant createdAt, Role... roles) {
        User u = new User();
        u.setUsername("u-" + UUID.randomUUID());
        u.setEmail(u.getUsername() + "@example.test");
        u.setTenantId(tenant);
        u.setStatus(status);
        u.setDeleted(deleted);
        u.setRoles(new HashSet<>(Arrays.asList(roles)));
        users.saveAndFlush(u);
        if (createdAt != null) {
            // createdAt is @CreationTimestamp + not updatable: rewrite it with a bulk update.
            em.createQuery("update User u set u.createdAt = :createdAt where u.id = :id")
                    .setParameter("createdAt", createdAt).setParameter("id", u.getId()).executeUpdate();
        }
        em.clear();
        return u;
    }

    private User student(UUID tenant, UserStatus status) {
        return user(tenant, status, false, null, Role.STUDENT);
    }

    // ---- studentStatusCounts ----

    @Test
    void studentStatusCountsSplitByStatusAndExcludeDeletedAndOtherRoles() {
        UUID tenant = UUID.randomUUID();
        student(tenant, UserStatus.ACTIVE);
        student(tenant, UserStatus.ACTIVE);
        user(tenant, UserStatus.ACTIVE, false, null, Role.STUDENT, Role.PROCTOR); // multi-role: once
        student(tenant, UserStatus.SUSPENDED);
        student(tenant, UserStatus.SUSPENDED);
        user(tenant, UserStatus.ACTIVE, true, null, Role.STUDENT); // soft-deleted
        user(tenant, UserStatus.SUSPENDED, true, null, Role.STUDENT); // soft-deleted
        user(tenant, UserStatus.ACTIVE, false, null, Role.HOST_ADMIN); // not a student

        StudentStatusCounts counts = service.studentStatusCounts(tenant);

        assertThat(counts).isEqualTo(new StudentStatusCounts(5, 3, 2));
    }

    @Test
    void studentStatusCountsNeverIncludeAnotherTenantsLearners() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        student(tenantA, UserStatus.ACTIVE);
        for (int i = 0; i < 4; i++) {
            student(tenantB, UserStatus.ACTIVE);
        }
        student(tenantB, UserStatus.SUSPENDED);

        assertThat(service.studentStatusCounts(tenantA)).isEqualTo(new StudentStatusCounts(1, 1, 0));
        assertThat(service.studentStatusCounts(tenantB)).isEqualTo(new StudentStatusCounts(5, 4, 1));
    }

    @Test
    void studentStatusCountsOfATenantWithoutLearnersAreZero() {
        student(UUID.randomUUID(), UserStatus.ACTIVE);

        assertThat(service.studentStatusCounts(UUID.randomUUID())).isEqualTo(new StudentStatusCounts(0, 0, 0));
    }

    @Test
    void platformUsersWithoutATenantAreNeverCountedForATenant() {
        UUID tenant = UUID.randomUUID();
        user(null, UserStatus.ACTIVE, false, null, Role.PLATFORM_ADMIN);
        student(tenant, UserStatus.ACTIVE);

        assertThat(service.studentStatusCounts(tenant).total()).isEqualTo(1);
    }

    // ---- countStudentsCreatedBetween ----

    @Test
    void createdBetweenIsHalfOpenAndCountsOnlyLiveStudentsOfTheTenant() {
        UUID tenant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        user(tenant, UserStatus.ACTIVE, false, FROM, Role.STUDENT); // lower bound included
        user(tenant, UserStatus.ACTIVE, false, TO.minusSeconds(1), Role.STUDENT);
        user(tenant, UserStatus.ACTIVE, false, Instant.parse("2026-09-15T12:00:00Z"), Role.STUDENT, Role.PROCTOR);
        user(tenant, UserStatus.ACTIVE, false, TO, Role.STUDENT); // upper bound excluded
        user(tenant, UserStatus.ACTIVE, false, FROM.minusSeconds(1), Role.STUDENT);
        user(tenant, UserStatus.ACTIVE, true, Instant.parse("2026-09-10T00:00:00Z"), Role.STUDENT); // deleted
        user(tenant, UserStatus.ACTIVE, false, Instant.parse("2026-09-10T00:00:00Z"), Role.EXAMINER);
        user(other, UserStatus.ACTIVE, false, Instant.parse("2026-09-10T00:00:00Z"), Role.STUDENT);

        assertThat(service.countStudentsCreatedBetween(tenant, FROM, TO)).isEqualTo(3);
        assertThat(service.countStudentsCreatedBetween(other, FROM, TO)).isEqualTo(1);
        assertThat(service.countStudentsCreatedBetween(UUID.randomUUID(), FROM, TO)).isZero();
    }

    @Test
    void adjacentRangesPartitionTheStudentsWithoutDoubleCounting() {
        UUID tenant = UUID.randomUUID();
        Instant middle = Instant.parse("2026-09-16T00:00:00Z");
        user(tenant, UserStatus.ACTIVE, false, FROM, Role.STUDENT);
        user(tenant, UserStatus.ACTIVE, false, middle, Role.STUDENT); // exactly on the shared boundary
        user(tenant, UserStatus.ACTIVE, false, TO.minusSeconds(1), Role.STUDENT);

        long first = service.countStudentsCreatedBetween(tenant, FROM, middle);
        long second = service.countStudentsCreatedBetween(tenant, middle, TO);

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(first + second).isEqualTo(service.countStudentsCreatedBetween(tenant, FROM, TO));
    }

    @Test
    void suspendedStudentsStillCountAsNewInTheWindow() {
        UUID tenant = UUID.randomUUID();
        user(tenant, UserStatus.SUSPENDED, false, Instant.parse("2026-09-10T00:00:00Z"), Role.STUDENT);

        assertThat(service.countStudentsCreatedBetween(tenant, FROM, TO)).isEqualTo(1);
    }

    // ---- activeStaffCounts ----

    @Test
    void activeStaffCountsOnlyActiveNonDeletedProctorsAndExaminers() {
        UUID tenant = UUID.randomUUID();
        user(tenant, UserStatus.ACTIVE, false, null, Role.PROCTOR);
        user(tenant, UserStatus.ACTIVE, false, null, Role.PROCTOR);
        user(tenant, UserStatus.SUSPENDED, false, null, Role.PROCTOR); // suspended
        user(tenant, UserStatus.ACTIVE, false, null, Role.EXAMINER);
        user(tenant, UserStatus.ACTIVE, false, null, Role.EXAMINER);
        user(tenant, UserStatus.ACTIVE, false, null, Role.EXAMINER);
        user(tenant, UserStatus.ACTIVE, true, null, Role.EXAMINER); // deleted
        user(tenant, UserStatus.ACTIVE, false, null, Role.PROCTOR, Role.EXAMINER); // counted in both
        student(tenant, UserStatus.ACTIVE);
        user(tenant, UserStatus.ACTIVE, false, null, Role.HOST_ADMIN);

        StaffCounts staff = service.activeStaffCounts(tenant);

        assertThat(staff).isEqualTo(new StaffCounts(3, 4));
    }

    @Test
    void activeStaffCountsAreTenantScopedAndZeroWhenAbsent() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            user(tenantB, UserStatus.ACTIVE, false, null, Role.PROCTOR);
        }
        user(tenantA, UserStatus.ACTIVE, false, null, Role.EXAMINER);

        assertThat(service.activeStaffCounts(tenantA)).isEqualTo(new StaffCounts(0, 1));
        assertThat(service.activeStaffCounts(tenantB)).isEqualTo(new StaffCounts(5, 0));
        assertThat(service.activeStaffCounts(UUID.randomUUID())).isEqualTo(new StaffCounts(0, 0));
    }
}
