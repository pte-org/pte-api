package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} only — this repository's tenant+program AND-scoping is
 * the single highest-severity correctness requirement in Phase 3 (a miss here
 * is a cross-tenant data leak that also poisons Phase 10's bulk-enroll input),
 * so it's proved against a live embedded database rather than inferred from
 * the query signature or a Mockito-mocked pass-through (same rationale as
 * {@code ProgramRepositoryTest}, the first user of this pattern).
 */
@DataJpaTest
class ClassStudentRepositoryTest {

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private TrainingClassRepository trainingClassRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private TrainingClass persistClassUnderNewTenant(String tenantName, String programName, String className) {
        Organization organization = new Organization();
        organization.setCode("tenant-" + UUID.randomUUID());
        organization.setName(tenantName + " " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization = organizationRepository.save(organization);

        Program program = new Program();
        program.setName(programName);
        program.setOrganization(organization);
        program = programRepository.save(program);

        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setName(className);
        trainingClass.setProgram(program);
        return trainingClassRepository.save(trainingClass);
    }

    private ClassStudent persistMembership(TrainingClass trainingClass, UUID tenantId) {
        ClassStudent membership = new ClassStudent();
        membership.setTrainingClass(trainingClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setOrganizationId(tenantId);
        return classStudentRepository.save(membership);
    }

    @Test
    void findByTenant_returnsOnlyThatTenantsRows() {
        TrainingClass tenantAClass = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        TrainingClass tenantBClass = persistClassUnderNewTenant("Tenant B", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAClass.getProgram().getOrganization().getPublicId();

        ClassStudent tenantAMembership = persistMembership(tenantAClass, tenantAPublicId);
        persistMembership(tenantBClass, tenantBClass.getProgram().getOrganization().getPublicId());

        List<ClassStudent> results = classStudentRepository
                .findByTrainingClass_Program_Organization_PublicId(tenantAPublicId);

        assertThat(results).extracting(ClassStudent::getPublicId)
                .containsExactly(tenantAMembership.getPublicId());
    }

    @Test
    void findByTenantAndProgram_returnsOnlyThatProgramsRowsWithinTenant() {
        TrainingClass tenantAProgram1Class = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAProgram1Class.getProgram().getOrganization().getPublicId();
        Organization tenantAOrganization = tenantAProgram1Class.getProgram().getOrganization();

        Program tenantAProgram2 = new Program();
        tenantAProgram2.setName("Khối 11");
        tenantAProgram2.setOrganization(tenantAOrganization);
        tenantAProgram2 = programRepository.save(tenantAProgram2);
        TrainingClass tenantAProgram2Class = new TrainingClass();
        tenantAProgram2Class.setName("11A1");
        tenantAProgram2Class.setProgram(tenantAProgram2);
        tenantAProgram2Class = trainingClassRepository.save(tenantAProgram2Class);

        ClassStudent program1Membership = persistMembership(tenantAProgram1Class, tenantAPublicId);
        persistMembership(tenantAProgram2Class, tenantAPublicId);

        List<ClassStudent> results = classStudentRepository
                .findByTrainingClass_Program_Organization_PublicIdAndTrainingClass_Program_PublicId(
                        tenantAPublicId, tenantAProgram1Class.getProgram().getPublicId());

        assertThat(results).extracting(ClassStudent::getPublicId)
                .containsExactly(program1Membership.getPublicId());
    }

    @Test
    void findByTenantAndProgram_crossTenantProgramId_returnsEmptyNeverTheOtherTenantsRoster() {
        TrainingClass tenantAClass = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAClass.getProgram().getOrganization().getPublicId();
        persistMembership(tenantAClass, tenantAPublicId);

        TrainingClass tenantBClass = persistClassUnderNewTenant("Tenant B", "Khối 12", "12A1");
        persistMembership(tenantBClass, tenantBClass.getProgram().getOrganization().getPublicId());
        UUID tenantBProgramPublicId = tenantBClass.getProgram().getPublicId();

        // Tenant A's caller passing Tenant B's programPublicId must get nothing back — never Tenant B's roster.
        List<ClassStudent> results = classStudentRepository
                .findByTrainingClass_Program_Organization_PublicIdAndTrainingClass_Program_PublicId(
                        tenantAPublicId, tenantBProgramPublicId);

        assertThat(results).isEmpty();
    }
}
