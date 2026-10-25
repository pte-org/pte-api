package com.pte.enrollment;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.repository.ClassStudentRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.identity.IdentityService;
import com.pte.tenancy.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * First public facade of the {@code enrollment} module (Plan B, Phase 4) —
 * {@code session} is the first-ever caller across the module boundary
 * ({@code session → enrollment}, a new one-way edge {@code ModuleStructureTest}
 * must accept).
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentModuleServiceTest {

    @Mock
    private TrainingClassRepository trainingClassRepository;
    @Mock
    private ClassStudentRepository classStudentRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private IdentityService identityService;

    private EnrollmentModuleService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentModuleService(trainingClassRepository, classStudentRepository, programRepository);
    }

    private TrainingClass classForTenant(UUID tenantPublicId) {
        Organization organization = new Organization();
        organization.setPublicId(tenantPublicId);
        Program program = new Program();
        program.setOrganization(organization);
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setProgram(program);
        return trainingClass;
    }

    @Test
    void findActiveStudentPublicIds_returnsAllMembersOfClass() {
        UUID tenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classForTenant(tenantId);
        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        ClassStudent m1 = new ClassStudent();
        m1.setStudentPublicId(UUID.randomUUID());
        ClassStudent m2 = new ClassStudent();
        m2.setStudentPublicId(UUID.randomUUID());
        when(classStudentRepository.findByOrganizationIdAndTrainingClass_PublicId(tenantId, classPublicId))
                .thenReturn(List.of(m1, m2));

        List<UUID> result = service.findActiveStudentPublicIds(tenantId, classPublicId);

        assertThat(result).containsExactlyInAnyOrder(m1.getStudentPublicId(), m2.getStudentPublicId());
    }

    @Test
    void findActiveStudentPublicIds_classFromOtherTenant_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classForTenant(otherTenantId);
        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));

        assertThatThrownBy(() -> service.findActiveStudentPublicIds(tenantId, classPublicId))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    @Test
    void findActiveStudentPublicIds_excludesIdentityRecordsThatAreNoLongerEligible() {
        UUID tenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classForTenant(tenantId);
        UUID activeStudentId = UUID.randomUUID();
        UUID suspendedStudentId = UUID.randomUUID();
        ClassStudent activeMembership = new ClassStudent();
        activeMembership.setStudentPublicId(activeStudentId);
        ClassStudent suspendedMembership = new ClassStudent();
        suspendedMembership.setStudentPublicId(suspendedStudentId);
        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classStudentRepository.findByOrganizationIdAndTrainingClass_PublicId(tenantId, classPublicId))
                .thenReturn(List.of(activeMembership, suspendedMembership));
        when(identityService.findActiveStudentPublicIds(tenantId, List.of(activeStudentId, suspendedStudentId)))
                .thenReturn(java.util.Set.of(activeStudentId));

        EnrollmentModuleService filteredService = new EnrollmentModuleService(
                trainingClassRepository, classStudentRepository, programRepository, identityService);

        assertThat(filteredService.findActiveStudentPublicIds(tenantId, classPublicId))
                .containsExactly(activeStudentId);
    }

    @Test
    void findActiveStudentPublicIds_unknownClass_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findActiveStudentPublicIds(tenantId, classPublicId))
                .isInstanceOf(StudentClassNotFoundException.class);
    }

    @Test
    void findActiveStudentPublicIdsByProgram_requiresActiveProgramInTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programForTenant(tenantId, programPublicId);
        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(classStudentRepository
                .findByTrainingClass_Program_Organization_PublicIdAndTrainingClass_Program_PublicId(
                        tenantId, programPublicId))
                .thenReturn(List.of());

        assertThat(service.findActiveStudentPublicIdsByProgram(tenantId, programPublicId)).isEmpty();
    }

    @Test
    void findActiveStudentPublicIdsByProgram_rejectsProgramFromOtherTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programForTenant(UUID.randomUUID(), programPublicId);
        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.findActiveStudentPublicIdsByProgram(tenantId, programPublicId))
                .isInstanceOf(com.pte.enrollment.internal.exception.ProgramNotFoundException.class);
    }

    private Program programForTenant(UUID tenantId, UUID programPublicId) {
        Organization organization = new Organization();
        organization.setPublicId(tenantId);
        Program program = new Program();
        program.setPublicId(programPublicId);
        program.setOrganization(organization);
        return program;
    }
}
