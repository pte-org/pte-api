package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.ClassLecturer;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.enrollment.internal.exception.LecturerAlreadyAssignedException;
import com.pte.enrollment.internal.exception.LecturerAssignmentNotFoundException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignLecturerRequest;
import com.pte.enrollment.internal.dto.response.LecturerAssignmentResponse;
import com.pte.enrollment.internal.repository.ClassLecturerRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.TrainingClassRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private TrainingClassRepository trainingClassRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ClassLecturerRepository classLecturerRepository;

    @Mock
    private AuditLogService auditLogService;

    private AssignmentService service;

    @BeforeEach
    void setUp() {
        // ClassService is the real class (not mocked) so findOwned's actual tenant-check logic
        // is exercised, not just a mocked pass-through — this is exactly the reuse-not-duplicate
        // discipline this phase's Design Constraints call for. Its own AuditLogService dependency
        // is passed as null (not this test's `auditLogService` mock) — AssignmentServiceTest never
        // calls a ClassService write method (only the package-private findOwned via
        // AssignmentService), so it's never invoked; the mock verified below is AssignmentService's
        // own, separate injected instance.
        ClassService classService = new ClassService(trainingClassRepository, programRepository, null);
        service = new AssignmentService(classService, classLecturerRepository, auditLogService);
    }

    private Organization organizationOf(UUID publicId) {
        Organization organization = new Organization();
        organization.setPublicId(publicId);
        organization.setName("Acme School");
        organization.setFacilityType(FacilityType.MAIN);
        return organization;
    }

    private Program programOf(UUID programPublicId, Organization organization) {
        Program program = new Program();
        program.setPublicId(programPublicId);
        program.setName("Khối 12");
        program.setOrganization(organization);
        return program;
    }

    private TrainingClass classOf(UUID classPublicId, Program program) {
        TrainingClass trainingClass = new TrainingClass();
        trainingClass.setPublicId(classPublicId);
        trainingClass.setName("12A1");
        trainingClass.setProgram(program);
        return trainingClass;
    }

    // --- Lecturer ---

    @Test
    void assignLecturer_saves() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        UUID assigneePublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.save(any(ClassLecturer.class))).thenAnswer(invocation -> {
            ClassLecturer saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        LecturerAssignmentResponse response = service.assignLecturer(organizationPublicId, programPublicId,
                classPublicId, new AssignLecturerRequest(assigneePublicId), caller);

        assertThat(response.classPublicId()).isEqualTo(classPublicId);
        assertThat(response.assigneePublicId()).isEqualTo(assigneePublicId);
        verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(EnrollmentConstants.EVENT_LECTURER_ASSIGNED), any());
    }

    @Test
    void assignLecturer_classBelongsToDifferentTenant_throwsNotFoundWithoutSaving() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = otherTenantId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(otherTenantId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));

        assertThatThrownBy(() -> service.assignLecturer(organizationPublicId, programPublicId, classPublicId,
                new AssignLecturerRequest(UUID.randomUUID()), caller))
                .isInstanceOf(StudentClassNotFoundException.class);

        verify(classLecturerRepository, never()).save(any());
    }

    @Test
    void assignLecturer_duplicateAssignment_rejectedByConstraint() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.save(any(ClassLecturer.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.assignLecturer(organizationPublicId, programPublicId, classPublicId,
                new AssignLecturerRequest(UUID.randomUUID()), caller))
                .isInstanceOf(LecturerAlreadyAssignedException.class);
    }

    @Test
    void listLecturers_returnsAssigneesForClass() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        ClassLecturer assignment = new ClassLecturer();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setAssigneePublicId(UUID.randomUUID());
        assignment.setTrainingClass(trainingClass);

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.findByOrganizationIdAndTrainingClass_PublicId(caller.tenantId(), classPublicId))
                .thenReturn(List.of(assignment));

        List<LecturerAssignmentResponse> result = service.listLecturers(organizationPublicId, programPublicId,
                classPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assigneePublicId()).isEqualTo(assignment.getAssigneePublicId());
    }

    @Test
    void unassignLecturer_deletes() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        UUID assignmentPublicId = UUID.randomUUID();
        ClassLecturer assignment = new ClassLecturer();
        assignment.setPublicId(assignmentPublicId);
        assignment.setTrainingClass(trainingClass);
        assignment.setAssigneePublicId(UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.findByPublicIdAndOrganizationId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.of(assignment));

        service.unassignLecturer(organizationPublicId, programPublicId, classPublicId, assignmentPublicId, caller);

        verify(classLecturerRepository).delete(assignment);
        verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(EnrollmentConstants.EVENT_LECTURER_UNASSIGNED), any());
    }

    @Test
    void unassignLecturer_notFound_throws() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        UUID assignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.findByPublicIdAndOrganizationId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignLecturer(organizationPublicId, programPublicId, classPublicId,
                assignmentPublicId, caller))
                .isInstanceOf(LecturerAssignmentNotFoundException.class);
    }

    @Test
    void unassignThenReassignLecturer_succeeds() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = tenantPublicId;
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(tenantPublicId));
        UUID classPublicId = UUID.randomUUID();
        TrainingClass trainingClass = classOf(classPublicId, program);
        UUID assigneePublicId = UUID.randomUUID();
        UUID firstAssignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        ClassLecturer firstAssignment = new ClassLecturer();
        firstAssignment.setPublicId(firstAssignmentPublicId);
        firstAssignment.setTrainingClass(trainingClass);
        firstAssignment.setAssigneePublicId(assigneePublicId);

        when(trainingClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(trainingClass));
        when(classLecturerRepository.findByPublicIdAndOrganizationId(firstAssignmentPublicId, caller.tenantId())).thenReturn(Optional.of(firstAssignment));

        service.unassignLecturer(organizationPublicId, programPublicId, classPublicId, firstAssignmentPublicId, caller);

        // Constraint released on delete — same assignee can be assigned again without the mocked
        // save throwing (a real DB would allow this once the row is gone).
        when(classLecturerRepository.save(any(ClassLecturer.class))).thenAnswer(invocation -> {
            ClassLecturer saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        LecturerAssignmentResponse response = service.assignLecturer(organizationPublicId, programPublicId,
                classPublicId, new AssignLecturerRequest(assigneePublicId), caller);

        assertThat(response.assigneePublicId()).isEqualTo(assigneePublicId);
    }

}
