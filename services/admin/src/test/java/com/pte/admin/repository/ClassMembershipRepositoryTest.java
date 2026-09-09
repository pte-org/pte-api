package com.pte.admin.repository;

import com.pte.admin.domain.ClassMembership;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.FacilityType;
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
class ClassMembershipRepositoryTest {

    @Autowired
    private ClassMembershipRepository classMembershipRepository;

    @Autowired
    private StudentClassRepository studentClassRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private StudentClass persistClassUnderNewTenant(String tenantName, String programName, String className) {
        Tenant tenant = new Tenant();
        tenant.setName(tenantName + " " + UUID.randomUUID());
        tenant.setOrganizationType("SCHOOL");
        tenant.setPackageName("starter");
        tenant.setStudentLimit(500);
        tenant = tenantRepository.save(tenant);

        Organization organization = new Organization();
        organization.setName("Downtown Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        organization = organizationRepository.save(organization);

        Program program = new Program();
        program.setName(programName);
        program.setOrganization(organization);
        program = programRepository.save(program);

        StudentClass studentClass = new StudentClass();
        studentClass.setName(className);
        studentClass.setProgram(program);
        return studentClassRepository.save(studentClass);
    }

    private ClassMembership persistMembership(StudentClass studentClass, UUID tenantId) {
        ClassMembership membership = new ClassMembership();
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setTenantId(tenantId);
        return classMembershipRepository.save(membership);
    }

    @Test
    void findByTenant_returnsOnlyThatTenantsRows() {
        StudentClass tenantAClass = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        StudentClass tenantBClass = persistClassUnderNewTenant("Tenant B", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAClass.getProgram().getOrganization().getTenant().getPublicId();

        ClassMembership tenantAMembership = persistMembership(tenantAClass, tenantAPublicId);
        persistMembership(tenantBClass, tenantBClass.getProgram().getOrganization().getTenant().getPublicId());

        List<ClassMembership> results = classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicId(tenantAPublicId);

        assertThat(results).extracting(ClassMembership::getPublicId)
                .containsExactly(tenantAMembership.getPublicId());
    }

    @Test
    void findByTenantAndProgram_returnsOnlyThatProgramsRowsWithinTenant() {
        StudentClass tenantAProgram1Class = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAProgram1Class.getProgram().getOrganization().getTenant().getPublicId();
        Organization tenantAOrganization = tenantAProgram1Class.getProgram().getOrganization();

        Program tenantAProgram2 = new Program();
        tenantAProgram2.setName("Khối 11");
        tenantAProgram2.setOrganization(tenantAOrganization);
        tenantAProgram2 = programRepository.save(tenantAProgram2);
        StudentClass tenantAProgram2Class = new StudentClass();
        tenantAProgram2Class.setName("11A1");
        tenantAProgram2Class.setProgram(tenantAProgram2);
        tenantAProgram2Class = studentClassRepository.save(tenantAProgram2Class);

        ClassMembership program1Membership = persistMembership(tenantAProgram1Class, tenantAPublicId);
        persistMembership(tenantAProgram2Class, tenantAPublicId);

        List<ClassMembership> results = classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantAPublicId, tenantAProgram1Class.getProgram().getPublicId());

        assertThat(results).extracting(ClassMembership::getPublicId)
                .containsExactly(program1Membership.getPublicId());
    }

    @Test
    void findByTenantAndProgram_crossTenantProgramId_returnsEmptyNeverTheOtherTenantsRoster() {
        StudentClass tenantAClass = persistClassUnderNewTenant("Tenant A", "Khối 12", "12A1");
        UUID tenantAPublicId = tenantAClass.getProgram().getOrganization().getTenant().getPublicId();
        persistMembership(tenantAClass, tenantAPublicId);

        StudentClass tenantBClass = persistClassUnderNewTenant("Tenant B", "Khối 12", "12A1");
        persistMembership(tenantBClass, tenantBClass.getProgram().getOrganization().getTenant().getPublicId());
        UUID tenantBProgramPublicId = tenantBClass.getProgram().getPublicId();

        // Tenant A's caller passing Tenant B's programPublicId must get nothing back — never Tenant B's roster.
        List<ClassMembership> results = classMembershipRepository
                .findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
                        tenantAPublicId, tenantBProgramPublicId);

        assertThat(results).isEmpty();
    }
}
