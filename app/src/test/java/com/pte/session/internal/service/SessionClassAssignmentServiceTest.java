package com.pte.session.internal.service;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.SessionClassAssignment;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.SessionClassAssignmentResponse;
import com.pte.session.internal.exception.ClassAssignmentNotAllowedException;
import com.pte.session.internal.exception.ClassAssignmentNotFoundException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.SessionClassAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionClassAssignmentServiceTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private EnrollmentModuleService enrollmentModuleService;
    @Mock
    private SessionClassAssignmentRepository assignmentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    private SessionClassAssignmentService service;
    private UUID tenantId;
    private CurrentUser hostAdmin;

    @BeforeEach
    void setUp() {
        service = new SessionClassAssignmentService(sessionLifecycleService, enrollmentService,
                enrollmentModuleService, assignmentRepository, enrollmentRepository);
        tenantId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private ExamSession scheduledSession(UUID publicId) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setPublicId(publicId);
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.SCHEDULED);
        return session;
    }

    @Test
    void assign_singleClass_enrollsAllItsStudents() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        List<UUID> students = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(students);
        when(assignmentRepository.existsBySessionIdAndClassPublicId(1L, classPublicId)).thenReturn(false);

        SessionClassAssignmentResponse response = service.assign(sessionPublicId, classPublicId, hostAdmin);

        ArgumentCaptor<BulkEnrollRequest> captor = ArgumentCaptor.forClass(BulkEnrollRequest.class);
        verify(enrollmentService).bulkEnroll(eq(sessionPublicId), captor.capture(), eq(hostAdmin));
        assertThat(captor.getValue().studentPublicIds()).containsExactlyInAnyOrderElementsOf(students);
        assertThat(response.classPublicId()).isEqualTo(classPublicId);
        verify(assignmentRepository).save(any(SessionClassAssignment.class));
    }

    @Test
    void assign_sessionNotScheduled_rejected() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        session.setStatus(SessionStatus.OPEN);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> service.assign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotAllowedException.class);

        verify(enrollmentModuleService, never()).findActiveStudentPublicIds(any(), any());
        verify(enrollmentService, never()).bulkEnroll(any(), any(), any());
    }

    @Test
    void assign_sameClassTwice_noDuplicateAssignmentRow_stillCallsBulkEnroll() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(assignmentRepository.existsBySessionIdAndClassPublicId(1L, classPublicId)).thenReturn(true);

        service.assign(sessionPublicId, classPublicId, hostAdmin);

        verify(enrollmentService, times(1)).bulkEnroll(any(), any(), any());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_againAfterNewMemberJoined_enrollsOnlyTheNewMember() {
        // bulkEnroll's own already-enrolled-skip logic (tested in EnrollmentServiceTest)
        // means passing the full current membership list again is safe — this
        // service's job is only to always forward the CURRENT membership list,
        // not to diff it itself.
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        UUID existingMember = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(existingMember, newMember));
        when(assignmentRepository.existsBySessionIdAndClassPublicId(1L, classPublicId)).thenReturn(true);

        service.assign(sessionPublicId, classPublicId, hostAdmin);

        ArgumentCaptor<BulkEnrollRequest> captor = ArgumentCaptor.forClass(BulkEnrollRequest.class);
        verify(enrollmentService).bulkEnroll(eq(sessionPublicId), captor.capture(), eq(hostAdmin));
        assertThat(captor.getValue().studentPublicIds()).containsExactlyInAnyOrder(existingMember, newMember);
    }

    @Test
    void assign_twoClasses_enrollmentCountEqualsDistinctStudents_allOnSameSnapshot() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        UUID sharedStudent = UUID.randomUUID();
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classA))
                .thenReturn(List.of(sharedStudent, UUID.randomUUID()));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classB))
                .thenReturn(List.of(sharedStudent));

        service.assign(sessionPublicId, classA, hostAdmin);
        service.assign(sessionPublicId, classB, hostAdmin);

        verify(enrollmentService, times(2)).bulkEnroll(eq(sessionPublicId), any(), eq(hostAdmin));
    }

    @Test
    void assign_emptyClass_succeedsWithNoEnrollment() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(List.of());

        SessionClassAssignmentResponse response = service.assign(sessionPublicId, classPublicId, hostAdmin);

        assertThat(response.classPublicId()).isEqualTo(classPublicId);
        verify(enrollmentService).bulkEnroll(eq(sessionPublicId), any(), eq(hostAdmin));
    }

    @Test
    void unassign_removesEnrollmentsOfCurrentClassMembersOnly() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        SessionClassAssignment assignment = new SessionClassAssignment();
        when(assignmentRepository.findBySessionIdAndClassPublicId(1L, classPublicId))
                .thenReturn(Optional.of(assignment));
        List<UUID> members = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(members);

        service.unassign(sessionPublicId, classPublicId, hostAdmin);

        verify(enrollmentRepository).deleteBySessionIdAndStudentPublicIdIn(1L, members);
        verify(assignmentRepository).delete(assignment);
    }

    @Test
    void unassign_classNotAssigned_throwsNotFound() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
        when(assignmentRepository.findBySessionIdAndClassPublicId(1L, classPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotFoundException.class);
    }

    @Test
    void unassign_sessionNotScheduled_rejected() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSession session = scheduledSession(sessionPublicId);
        session.setStatus(SessionStatus.CLOSED);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> service.unassign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotAllowedException.class);

        verify(assignmentRepository, never()).delete(any());
    }
}
