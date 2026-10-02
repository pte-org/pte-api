package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.StudentClass;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StudentRosterRepositoryTest {

    @Autowired
    private StudentRosterRepository rosterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createIdentityReadView() {
        jdbcTemplate.execute("""
                CREATE VIEW identity_student_directory AS
                SELECT u.id, u.public_id, u.email, u.full_name, u.student_code, u.phone,
                       u.status, u.created_at, u.tenant_id, u.deleted, u.username,
                       u.must_change_password
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id AND ur.role = 'STUDENT'
                """);
    }

    @Test
    void sameCreatedAt_usesStudentPublicIdTieBreakSoPagesDoNotRepeatRows() {
        Tenant tenant = new Tenant();
        tenant.setCode("roster-" + UUID.randomUUID());
        tenant.setName("Roster Tenant " + UUID.randomUUID());
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(100);
        tenant = tenantRepository.saveAndFlush(tenant);

        Organization organization = new Organization();
        organization.setName("Roster Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        organizationRepository.saveAndFlush(organization);

        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        User first = student("first@example.test", tenant.getPublicId(), createdAt);
        User second = student("second@example.test", tenant.getPublicId(), createdAt);
        userRepository.saveAndFlush(first);
        userRepository.saveAndFlush(second);

        Page<StudentRosterRow> firstPage = rosterRepository.findPageForTenant(
                tenant.getPublicId(), "", null, null, "ALL", "CREATED_AT", "ASC", PageRequest.of(0, 1));
        Page<StudentRosterRow> secondPage = rosterRepository.findPageForTenant(
                tenant.getPublicId(), "", null, null, "ALL", "CREATED_AT", "ASC", PageRequest.of(1, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().get(0).getStudentPublicId())
                .isNotEqualTo(firstPage.getContent().get(0).getStudentPublicId());
    }

    private User student(String email, UUID tenantId, Instant createdAt) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setTenantId(tenantId);
        user.setRoles(new HashSet<>(java.util.Set.of(Role.STUDENT)));
        user.setCreatedAt(createdAt);
        return user;
    }
}
