package com.pte.enrollment.internal.repository;

import com.pte.enrollment.EnrollmentOverviewService;
import com.pte.enrollment.EnrollmentOverviewService.AcademicCounts;
import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
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
    private TrainingClassRepository classes;

    @Autowired
    private ClassStudentRepository memberships;

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

    private TrainingClass trainingClass(Program program, ClassStatus status, boolean deleted) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setName("Class " + UUID.randomUUID());
        trainingClass.setProgram(program);
        trainingClass.setStatus(status);
        trainingClass.setDeleted(deleted);
        return classes.save(trainingClass);
    }

    private ClassStudent membership(TrainingClass trainingClass, UUID tenantPublicId, boolean deleted) {
        ClassStudent membership = new ClassStudent();
        membership.setTrainingClass(trainingClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setOrganizationId(tenantPublicId);
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
        trainingClass(live, ClassStatus.ACTIVE, false);
        trainingClass(live, ClassStatus.ACTIVE, false);
        trainingClass(live, ClassStatus.INACTIVE, false);
        trainingClass(live, ClassStatus.SUSPENDED, false);
        trainingClass(live, ClassStatus.ACTIVE, true); // class archived
        trainingClass(archived, ClassStatus.ACTIVE, false); // program archived

        assertThat(service.academicCounts(branch.getPublicId()).activeClasses()).isEqualTo(2);
    }

    @Test
    void assignedLearnersCountLiveMembershipsInLiveClassesOnly() {
        Organization tenant = organization();
        UUID tenantId = tenant.getPublicId();
        Program program = program(tenant, ProgramStatus.ACTIVE, false);
        TrainingClass live = trainingClass(program, ClassStatus.ACTIVE, false);
        TrainingClass deletedClass = trainingClass(program, ClassStatus.ACTIVE, true);
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
        TrainingClass classA = trainingClass(programA, ClassStatus.ACTIVE, false);
        membership(classA, tenantA.getPublicId(), false);

        Organization branchB = tenantB;
        for (int i = 0; i < 3; i++) {
            Program programB = program(branchB, ProgramStatus.ACTIVE, false);
            TrainingClass classB = trainingClass(programB, ClassStatus.ACTIVE, false);
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
