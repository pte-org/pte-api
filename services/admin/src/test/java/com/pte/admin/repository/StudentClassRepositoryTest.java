package com.pte.admin.repository;

import com.pte.admin.domain.ClassMembership;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Program;
import com.pte.admin.domain.StudentClass;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.FacilityType;
import com.pte.admin.dto.response.ClassStudentCountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@code @DataJpaTest} only — {@code countStudentsByClassForProgram} is the
 * first {@code SELECT new ...} constructor-expression + {@code LEFT JOIN} +
 * {@code GROUP BY} query in this codebase (Phase 13's dashboard). A Mockito
 * mock of the repository (as {@code ProgramServiceTest} uses) proves the
 * service layer consumes the result correctly, but never actually runs the
 * JPQL — this test proves the query itself is correct against a live
 * embedded database, same rationale as {@code ClassMembershipRepositoryTest}.
 */
@DataJpaTest
class StudentClassRepositoryTest {

    @Autowired
    private StudentClassRepository studentClassRepository;

    @Autowired
    private ClassMembershipRepository classMembershipRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private Program persistProgram(String programName) {
        Tenant tenant = new Tenant();
        tenant.setName("Acme School " + UUID.randomUUID());
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
        return programRepository.save(program);
    }

    private StudentClass persistClass(Program program, String className) {
        StudentClass studentClass = new StudentClass();
        studentClass.setName(className);
        studentClass.setProgram(program);
        return studentClassRepository.save(studentClass);
    }

    private void persistMembership(StudentClass studentClass, UUID tenantId) {
        ClassMembership membership = new ClassMembership();
        membership.setStudentClass(studentClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setTenantId(tenantId);
        classMembershipRepository.save(membership);
    }

    @Test
    void countStudentsByClassForProgram_returnsZeroForClassWithNoMembers_notAMissingRow() {
        Program program = persistProgram("Khối 12");
        StudentClass emptyClass = persistClass(program, "12A1");

        List<ClassStudentCountResponse> rows = studentClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId, ClassStudentCountResponse::studentCount)
                .containsExactly(tuple(emptyClass.getPublicId(), 0L));
    }

    @Test
    void countStudentsByClassForProgram_returnsCorrectCountPerClass() {
        Program program = persistProgram("Khối 12");
        UUID tenantId = program.getOrganization().getTenant().getPublicId();
        StudentClass classA = persistClass(program, "12A1");
        StudentClass classB = persistClass(program, "12A2");
        persistMembership(classA, tenantId);
        persistMembership(classA, tenantId);
        persistMembership(classB, tenantId);

        List<ClassStudentCountResponse> rows = studentClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId, ClassStudentCountResponse::studentCount)
                .containsExactlyInAnyOrder(
                        tuple(classA.getPublicId(), 2L),
                        tuple(classB.getPublicId(), 1L));
    }

    @Test
    void countStudentsByClassForProgram_excludesArchivedClasses() {
        Program program = persistProgram("Khối 12");
        StudentClass activeClass = persistClass(program, "12A1");
        StudentClass archivedClass = persistClass(program, "12A2");
        archivedClass.setDeleted(true);
        studentClassRepository.save(archivedClass);

        List<ClassStudentCountResponse> rows = studentClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId)
                .containsExactly(activeClass.getPublicId());
    }

    @Test
    void countStudentsByClassForProgram_excludesClassesFromOtherPrograms() {
        Program programA = persistProgram("Khối 12");
        Program programB = persistProgram("Khối 11");
        StudentClass classUnderA = persistClass(programA, "12A1");
        persistClass(programB, "11A1");

        List<ClassStudentCountResponse> rows = studentClassRepository.countStudentsByClassForProgram(
                programA.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId)
                .containsExactly(classUnderA.getPublicId());
    }
}
