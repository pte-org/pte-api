package com.pte.enrollment.internal.repository;

import com.pte.enrollment.EnrollmentOverviewService;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.domain.enums.ClassStatus;
import com.pte.enrollment.domain.enums.ProgramStatus;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Academic-structure counts behind {@link EnrollmentOverviewService}: soft-delete
 * and status filtering plus tenant isolation through organization -> tenant.
 */
@DataJpaTest
@Import(EnrollmentOverviewService.class)
class EnrollmentOverviewQueriesTest {

    @Autowired
    private EnrollmentOverviewService service;

    @Autowired
    private EntityManager em;

    @Autowired
    private ProgramRepository programs;

    @Autowired
    private StudentClassRepository classes;

    @Autowired
    private ClassMembershipRepository memberships;

    private Organization organization() {
        Organization organization = new Organization();
        organization.setCode("t-" + UUID.randomUUID());
        organization.setName("Tenant " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        em.persist(organization);
        em.flush();
        return organization;
    }

    private Program program(Organization organization, ProgramStatus status, boolean deleted) {
        Program program = new Program();
        program.setName("Program " + UUID.randomUUID());
        program.setOrganization(organization);
        program.setStatus(status);
        program.setDeleted(deleted);
        return programs.save(program);
    }

    private StudentClass studentClass(Program program, ClassStatus status, boolean deleted) {
        StudentClass studentClass = new StudentClass();
        studentClass.setName("Class " + UUID.randomUUID());
        studentClass.setProgram(program);
        studentClass.setStatus(status);
        studentClass.setDeleted(deleted);
        return classes.save(studentClass);
    }

    private ClassMembership membership(StudentClass studentClass, UUID tenantPublicId, boolean deleted) {
        ClassMembership membership = new ClassMembership();
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setTenantId(tenantPublicId);
        membership.setDeleted(deleted);
        return memberships.save(membership);
    }

    @Test
    void programsCountOnlyActiveNonDeletedProgramsOfTheTenant() {
        Organization branch = organization();
        program(branch, ProgramStatus.ACTIVE, false);
        program(branch, ProgramStatus.ACTIVE, false);
        program(branch, ProgramStatus.ACTIVE, false);
        program(branch, ProgramStatus.INACTIVE, false);
        program(branch, ProgramStatus.SUSPENDED, false);
        program(branch, ProgramStatus.ACTIVE, true); // archived

        assertThat(service.academicCounts(branch.getPublicId()).programs()).isEqualTo(3);
    }

    @Test
    void activeClassesExcludeInactiveSuspendedDeletedAndThoseOfDeletedPrograms() {
        Organization branch = organization();
        Program live = program(branch, ProgramStatus.ACTIVE, false);
        Program archived = program(branch, ProgramStatus.ACTIVE, true);
        studentClass(live, ClassStatus.ACTIVE, false);
        studentClass(live, ClassStatus.ACTIVE, false);
        studentClass(live, ClassStatus.INACTIVE, false);
        studentClass(live, ClassStatus.SUSPENDED, false);
        studentClass(live, ClassStatus.ACTIVE, true); // class archived
        studentClass(archived, ClassStatus.ACTIVE, false); // program archived

        assertThat(service.academicCounts(branch.getPublicId()).activeClasses()).isEqualTo(2);
    }

    @Test
    void assignedLearnersCountLiveMembershipsInLiveClassesOnly() {
        Organization tenant = organization();
        UUID tenantId = tenant.getPublicId();
        Program program = program(tenant, ProgramStatus.ACTIVE, false);
        StudentClass live = studentClass(program, ClassStatus.ACTIVE, false);
        StudentClass deletedClass = studentClass(program, ClassStatus.ACTIVE, true);
        membership(live, tenantId, false);
        membership(live, tenantId, false);
        membership(live, tenantId, true); // membership removed
        membership(deletedClass, tenantId, false); // class archived

        assertThat(service.academicCounts(tenantId).assignedLearners()).isEqualTo(2);
    }

    @Test
    void countsNeverCrossTheTenantBoundary() {
        Organization tenantA = organization();
        Organization tenantB = organization();
        Program programA = program(tenantA, ProgramStatus.ACTIVE, false);
        StudentClass classA = studentClass(programA, ClassStatus.ACTIVE, false);
        membership(classA, tenantA.getPublicId(), false);

        Organization branchB = tenantB;
        for (int i = 0; i < 3; i++) {
            Program programB = program(branchB, ProgramStatus.ACTIVE, false);
            StudentClass classB = studentClass(programB, ClassStatus.ACTIVE, false);
            membership(classB, tenantB.getPublicId(), false);
            membership(classB, tenantB.getPublicId(), false);
        }

        assertThat(service.academicCounts(tenantA.getPublicId())).isEqualTo(new AcademicCounts(1, 1, 1));
        assertThat(service.academicCounts(tenantB.getPublicId())).isEqualTo(new AcademicCounts(3, 3, 6));
    }

    @Test
    void aTenantWithNoAcademicStructureHasAllZeroCounts() {
        program(organization(), ProgramStatus.ACTIVE, false);

        assertThat(service.academicCounts(UUID.randomUUID())).isEqualTo(new AcademicCounts(0, 0, 0));
    }
}
