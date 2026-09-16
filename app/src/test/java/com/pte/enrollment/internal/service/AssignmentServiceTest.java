package com.pte.enrollment.internal.service;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.enrollment.domain.LecturerAssignment;
import com.pte.tenancy.domain.Organization;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.ProgramCoordinatorAssignment;
import com.pte.enrollment.domain.StudentClass;
import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.FacilityType;
import com.pte.enrollment.internal.exception.CoordinatorAlreadyAssignedException;
import com.pte.enrollment.internal.exception.CoordinatorAssignmentNotFoundException;
import com.pte.enrollment.internal.exception.LecturerAlreadyAssignedException;
import com.pte.enrollment.internal.exception.LecturerAssignmentNotFoundException;
import com.pte.enrollment.internal.exception.ProgramNotFoundException;
import com.pte.enrollment.internal.exception.StudentClassNotFoundException;
import com.pte.enrollment.internal.dto.request.AssignCoordinatorRequest;
import com.pte.enrollment.internal.dto.request.AssignLecturerRequest;
import com.pte.enrollment.internal.dto.response.LecturerAssignmentResponse;
import com.pte.enrollment.internal.dto.response.ProgramCoordinatorAssignmentResponse;
import com.pte.enrollment.internal.repository.LecturerAssignmentRepository;
import com.pte.enrollment.internal.repository.ProgramCoordinatorAssignmentRepository;
import com.pte.enrollment.internal.repository.ProgramRepository;
import com.pte.enrollment.internal.repository.StudentClassRepository;
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
    private StudentClassRepository studentClassRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private LecturerAssignmentRepository lecturerAssignmentRepository;

    @Mock
    private ProgramCoordinatorAssignmentRepository coordinatorAssignmentRepository;


    @Mock
    private AuditLogService auditLogService;

    private AssignmentService service;

    @BeforeEach
    void setUp() {
        // ClassService/ProgramService are the real classes (not mocked) so findOwned's actual
        // tenant-check logic is exercised, not just a mocked pass-through — this is exactly the
        // reuse-not-duplicate discipline this phase's Design Constraints call for. Their own
        // AuditLogService dependency is passed as null (not this test's `auditLogService` mock) —
        // AssignmentServiceTest never calls a ClassService/ProgramService write method (only the
        // package-private findOwned via AssignmentService), so it's never invoked; the mock
        // verified below is AssignmentService's own, separate injected instance.
        ClassService classService = new ClassService(studentClassRepository, null, programRepository, null, null);
        ProgramService programService = new ProgramService(programRepository, null, null, null);
        service = new AssignmentService(classService, programService, lecturerAssignmentRepository,
                coordinatorAssignmentRepository, auditLogService);
    }

    private Tenant tenantWithPublicId(UUID publicId) {
        Tenant tenant = new Tenant();
        tenant.setPublicId(publicId);
        tenant.setName("Acme School");
        return tenant;
    }

    private Organization organizationOf(UUID orgPublicId, Tenant tenant) {
        Organization organization = new Organization();
        organization.setPublicId(orgPublicId);
        organization.setName("Downtown Branch");
        organization.setFacilityType(FacilityType.BRANCH);
        organization.setTenant(tenant);
        return organization;
    }

    private Program programOf(UUID programPublicId, Organization organization) {
        Program program = new Program();
        program.setPublicId(programPublicId);
        program.setName("Khối 12");
        program.setOrganization(organization);
        return program;
    }

    private StudentClass classOf(UUID classPublicId, Program program) {
        StudentClass studentClass = new StudentClass();
        studentClass.setPublicId(classPublicId);
        studentClass.setName("12A1");
        studentClass.setProgram(program);
        return studentClass;
    }

    // --- Lecturer ---

    @Test
    void assignLecturer_saves() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID assigneePublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.save(any(LecturerAssignment.class))).thenAnswer(invocation -> {
            LecturerAssignment saved = invocation.getArgument(0);
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
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));

        assertThatThrownBy(() -> service.assignLecturer(organizationPublicId, programPublicId, classPublicId,
                new AssignLecturerRequest(UUID.randomUUID()), caller))
                .isInstanceOf(StudentClassNotFoundException.class);

        verify(lecturerAssignmentRepository, never()).save(any());
    }

    @Test
    void assignLecturer_duplicateAssignment_rejectedByConstraint() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.save(any(LecturerAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.assignLecturer(organizationPublicId, programPublicId, classPublicId,
                new AssignLecturerRequest(UUID.randomUUID()), caller))
                .isInstanceOf(LecturerAlreadyAssignedException.class);
    }

    @Test
    void listLecturers_returnsAssigneesForClass() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        LecturerAssignment assignment = new LecturerAssignment();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setAssigneePublicId(UUID.randomUUID());
        assignment.setStudentClass(studentClass);

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.findByTenantIdAndStudentClass_PublicId(caller.tenantId(), classPublicId))
                .thenReturn(List.of(assignment));

        List<LecturerAssignmentResponse> result = service.listLecturers(organizationPublicId, programPublicId,
                classPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assigneePublicId()).isEqualTo(assignment.getAssigneePublicId());
    }

    @Test
    void unassignLecturer_deletes() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID assignmentPublicId = UUID.randomUUID();
        LecturerAssignment assignment = new LecturerAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setStudentClass(studentClass);
        assignment.setAssigneePublicId(UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.of(assignment));

        service.unassignLecturer(organizationPublicId, programPublicId, classPublicId, assignmentPublicId, caller);

        verify(lecturerAssignmentRepository).delete(assignment);
        verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_CLASS), eq(classPublicId.toString()),
                eq(EnrollmentConstants.EVENT_LECTURER_UNASSIGNED), any());
    }

    @Test
    void unassignLecturer_notFound_throws() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID assignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignLecturer(organizationPublicId, programPublicId, classPublicId,
                assignmentPublicId, caller))
                .isInstanceOf(LecturerAssignmentNotFoundException.class);
    }

    @Test
    void unassignThenReassignLecturer_succeeds() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID classPublicId = UUID.randomUUID();
        StudentClass studentClass = classOf(classPublicId, program);
        UUID assigneePublicId = UUID.randomUUID();
        UUID firstAssignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        LecturerAssignment firstAssignment = new LecturerAssignment();
        firstAssignment.setPublicId(firstAssignmentPublicId);
        firstAssignment.setStudentClass(studentClass);
        firstAssignment.setAssigneePublicId(assigneePublicId);

        when(studentClassRepository.findByPublicId(classPublicId)).thenReturn(Optional.of(studentClass));
        when(lecturerAssignmentRepository.findByPublicIdAndTenantId(firstAssignmentPublicId, caller.tenantId())).thenReturn(Optional.of(firstAssignment));

        service.unassignLecturer(organizationPublicId, programPublicId, classPublicId, firstAssignmentPublicId, caller);

        // Constraint released on delete — same assignee can be assigned again without the mocked
        // save throwing (a real DB would allow this once the row is gone).
        when(lecturerAssignmentRepository.save(any(LecturerAssignment.class))).thenAnswer(invocation -> {
            LecturerAssignment saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        LecturerAssignmentResponse response = service.assignLecturer(organizationPublicId, programPublicId,
                classPublicId, new AssignLecturerRequest(assigneePublicId), caller);

        assertThat(response.assigneePublicId()).isEqualTo(assigneePublicId);
    }

    // --- Coordinator ---

    @Test
    void assignCoordinator_saves() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID assigneePublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.save(any(ProgramCoordinatorAssignment.class))).thenAnswer(invocation -> {
            ProgramCoordinatorAssignment saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ProgramCoordinatorAssignmentResponse response = service.assignCoordinator(organizationPublicId,
                programPublicId, new AssignCoordinatorRequest(assigneePublicId), caller);

        assertThat(response.programPublicId()).isEqualTo(programPublicId);
        assertThat(response.assigneePublicId()).isEqualTo(assigneePublicId);
        verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(EnrollmentConstants.EVENT_COORDINATOR_ASSIGNED), any());
    }

    @Test
    void assignCoordinator_programBelongsToDifferentTenant_throwsNotFoundWithoutSaving() {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(otherTenantId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> service.assignCoordinator(organizationPublicId, programPublicId,
                new AssignCoordinatorRequest(UUID.randomUUID()), caller))
                .isInstanceOf(ProgramNotFoundException.class);

        verify(coordinatorAssignmentRepository, never()).save(any());
    }

    @Test
    void assignCoordinator_duplicateAssignment_rejectedByConstraint() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.save(any(ProgramCoordinatorAssignment.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.assignCoordinator(organizationPublicId, programPublicId,
                new AssignCoordinatorRequest(UUID.randomUUID()), caller))
                .isInstanceOf(CoordinatorAlreadyAssignedException.class);
    }

    @Test
    void listCoordinators_returnsAssigneesForProgram() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        ProgramCoordinatorAssignment assignment = new ProgramCoordinatorAssignment();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setAssigneePublicId(UUID.randomUUID());
        assignment.setProgram(program);

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.findByTenantIdAndProgram_PublicId(caller.tenantId(), programPublicId))
                .thenReturn(List.of(assignment));

        List<ProgramCoordinatorAssignmentResponse> result = service.listCoordinators(organizationPublicId,
                programPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assigneePublicId()).isEqualTo(assignment.getAssigneePublicId());
    }

    @Test
    void unassignCoordinator_deletes() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID assignmentPublicId = UUID.randomUUID();
        ProgramCoordinatorAssignment assignment = new ProgramCoordinatorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setProgram(program);
        assignment.setAssigneePublicId(UUID.randomUUID());
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.of(assignment));

        service.unassignCoordinator(organizationPublicId, programPublicId, assignmentPublicId, caller);

        verify(coordinatorAssignmentRepository).delete(assignment);
        verify(auditLogService).record(eq(caller), eq(EnrollmentConstants.AGGREGATE_PROGRAM), eq(programPublicId.toString()),
                eq(EnrollmentConstants.EVENT_COORDINATOR_UNASSIGNED), any());
    }

    @Test
    void unassignCoordinator_notFound_throws() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID assignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.findByPublicIdAndTenantId(assignmentPublicId, caller.tenantId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassignCoordinator(organizationPublicId, programPublicId,
                assignmentPublicId, caller))
                .isInstanceOf(CoordinatorAssignmentNotFoundException.class);
    }

    @Test
    void unassignThenReassignCoordinator_succeeds() {
        UUID tenantPublicId = UUID.randomUUID();
        UUID organizationPublicId = UUID.randomUUID();
        UUID programPublicId = UUID.randomUUID();
        Program program = programOf(programPublicId, organizationOf(organizationPublicId, tenantWithPublicId(tenantPublicId)));
        UUID assigneePublicId = UUID.randomUUID();
        UUID firstAssignmentPublicId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantPublicId, List.of("HOST_ADMIN"));

        ProgramCoordinatorAssignment firstAssignment = new ProgramCoordinatorAssignment();
        firstAssignment.setPublicId(firstAssignmentPublicId);
        firstAssignment.setProgram(program);
        firstAssignment.setAssigneePublicId(assigneePublicId);

        when(programRepository.findByPublicId(programPublicId)).thenReturn(Optional.of(program));
        when(coordinatorAssignmentRepository.findByPublicIdAndTenantId(firstAssignmentPublicId, caller.tenantId()))
                .thenReturn(Optional.of(firstAssignment));

        service.unassignCoordinator(organizationPublicId, programPublicId, firstAssignmentPublicId, caller);

        when(coordinatorAssignmentRepository.save(any(ProgramCoordinatorAssignment.class))).thenAnswer(invocation -> {
            ProgramCoordinatorAssignment saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        ProgramCoordinatorAssignmentResponse response = service.assignCoordinator(organizationPublicId,
                programPublicId, new AssignCoordinatorRequest(assigneePublicId), caller);

        assertThat(response.assigneePublicId()).isEqualTo(assigneePublicId);
    }
}
