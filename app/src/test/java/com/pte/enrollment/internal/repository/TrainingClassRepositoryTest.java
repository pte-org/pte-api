package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.enrollment.internal.dto.response.ClassStudentCountResponse;
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
 * embedded database, same rationale as {@code ClassStudentRepositoryTest}.
 */
@DataJpaTest
class TrainingClassRepositoryTest {

    @Autowired
    private TrainingClassRepository trainingClassRepository;

    @Autowired
    private ClassStudentRepository classStudentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private Program persistProgram(String programName) {
        Organization organization = new Organization();
        organization.setCode("acme-" + UUID.randomUUID());
        organization.setName("Acme School " + UUID.randomUUID());
        organization.setOrganizationType("SCHOOL");
        organization.setPackageName("starter");
        organization.setStudentLimit(500);
        organization.setFacilityType(FacilityType.MAIN);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization = organizationRepository.save(organization);

        Program program = new Program();
        program.setName(programName);
        program.setOrganization(organization);
        return programRepository.save(program);
    }

    private TrainingClass persistClass(Program program, String className) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setName(className);
        trainingClass.setProgram(program);
        return trainingClassRepository.save(trainingClass);
    }

    private void persistMembership(TrainingClass trainingClass, UUID tenantId) {
        ClassStudent membership = new ClassStudent();
        membership.setTrainingClass(trainingClass);
        membership.setStudentPublicId(UUID.randomUUID());
        membership.setOrganizationId(tenantId);
        classStudentRepository.save(membership);
    }

    @Test
    void countStudentsByClassForProgram_returnsZeroForClassWithNoMembers_notAMissingRow() {
        Program program = persistProgram("Khối 12");
        TrainingClass emptyClass = persistClass(program, "12A1");

        List<ClassStudentCountResponse> rows = trainingClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId, ClassStudentCountResponse::studentCount)
                .containsExactly(tuple(emptyClass.getPublicId(), 0L));
    }

    @Test
    void countStudentsByClassForProgram_returnsCorrectCountPerClass() {
        Program program = persistProgram("Khối 12");
        UUID tenantId = program.getOrganization().getPublicId();
        TrainingClass classA = persistClass(program, "12A1");
        TrainingClass classB = persistClass(program, "12A2");
        persistMembership(classA, tenantId);
        persistMembership(classA, tenantId);
        persistMembership(classB, tenantId);

        List<ClassStudentCountResponse> rows = trainingClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId, ClassStudentCountResponse::studentCount)
                .containsExactlyInAnyOrder(
                        tuple(classA.getPublicId(), 2L),
                        tuple(classB.getPublicId(), 1L));
    }

    @Test
    void countStudentsByClassForProgram_excludesArchivedClasses() {
        Program program = persistProgram("Khối 12");
        TrainingClass activeClass = persistClass(program, "12A1");
        TrainingClass archivedClass = persistClass(program, "12A2");
        archivedClass.setDeleted(true);
        trainingClassRepository.save(archivedClass);

        List<ClassStudentCountResponse> rows = trainingClassRepository.countStudentsByClassForProgram(
                program.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId)
                .containsExactly(activeClass.getPublicId());
    }

    @Test
    void countStudentsByClassForProgram_excludesClassesFromOtherPrograms() {
        Program programA = persistProgram("Khối 12");
        Program programB = persistProgram("Khối 11");
        TrainingClass classUnderA = persistClass(programA, "12A1");
        persistClass(programB, "11A1");

        List<ClassStudentCountResponse> rows = trainingClassRepository.countStudentsByClassForProgram(
                programA.getPublicId());

        assertThat(rows).extracting(ClassStudentCountResponse::classPublicId)
                .containsExactly(classUnderA.getPublicId());
    }
}
