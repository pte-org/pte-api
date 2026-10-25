package com.pte.enrollment.internal.repository;

import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.repository.OrganizationMemberRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
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
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createIdentityReadView() {
        jdbcTemplate.execute("""
                CREATE VIEW identity_student_directory AS
                SELECT u.id, u.public_id, u.email, u.full_name, u.student_code, u.phone,
                       om.status, om.created_at, om.organization_id AS tenant_id, u.deleted, u.username,
                       u.must_change_password
                FROM users u
                JOIN organization_members om ON om.user_id = u.id AND om.deleted = FALSE
                JOIN organization_member_roles omr ON omr.member_id = om.id AND omr.role = 'STUDENT'
                """);
    }

    @Test
    void sameCreatedAt_usesStudentPublicIdTieBreakSoPagesDoNotRepeatRows() {
        Organization organization = new Organization();
        organization.setCode("roster-" + UUID.randomUUID());
        organization.setName("Roster Tenant " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(100);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization = organizationRepository.saveAndFlush(organization);

        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        User first = student("first@example.test", createdAt);
        User second = student("second@example.test", createdAt);
        first.setPhone("09120001");
        second.setPhone("09120002");
        userRepository.saveAndFlush(first);
        userRepository.saveAndFlush(second);
        enroll(first, organization.getPublicId());
        enroll(second, organization.getPublicId());

        Page<StudentRosterRow> firstPage = rosterRepository.findPageForTenant(
                organization.getPublicId(), "", null, null, "ALL", "ALL", "CREATED_AT", "ASC", PageRequest.of(0, 1));
        Page<StudentRosterRow> secondPage = rosterRepository.findPageForTenant(
                organization.getPublicId(), "", null, null, "ALL", "ALL", "CREATED_AT", "ASC", PageRequest.of(1, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().get(0).getStudentPublicId())
                .isNotEqualTo(firstPage.getContent().get(0).getStudentPublicId());

        Page<StudentRosterRow> phoneSearch = rosterRepository.findPageForTenant(
                organization.getPublicId(), "09120001", null, null, "ALL", "ALL", "CREATED_AT", "ASC",
                PageRequest.of(0, 10));

        assertThat(phoneSearch.getContent()).singleElement()
                .extracting(StudentRosterRow::getStudentPublicId)
                .isEqualTo(first.getPublicId().toString());
    }

    private User student(String email, Instant createdAt) {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setCreatedAt(createdAt);
        return user;
    }

    private void enroll(User user, UUID organizationPublicId) {
        OrganizationMember member = new OrganizationMember();
        member.setUser(user);
        member.setOrganizationId(organizationPublicId);
        member.setRoles(new HashSet<>(java.util.Set.of(Role.STUDENT)));
        memberRepository.saveAndFlush(member);
    }
}
