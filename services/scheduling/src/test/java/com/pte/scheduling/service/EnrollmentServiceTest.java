package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.domain.Enrollment;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.ProctorAssignment;
import com.pte.scheduling.domain.enums.ProctorRole;
import com.pte.scheduling.domain.exception.AlreadyEnrolledException;
import com.pte.scheduling.domain.exception.EnrollmentNotFoundException;
import com.pte.scheduling.domain.exception.ProctorAssignmentNotFoundException;
import com.pte.scheduling.dto.request.AssignProctorRequest;
import com.pte.scheduling.dto.request.BulkEnrollRequest;
import com.pte.scheduling.dto.request.EnrollStudentRequest;
import com.pte.scheduling.dto.request.UpdateProctorRoleRequest;
import com.pte.scheduling.dto.response.BulkEnrollResponse;
import com.pte.scheduling.dto.response.EnrollmentResponse;
import com.pte.scheduling.dto.response.ProctorAssignmentResponse;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import com.pte.scheduling.repository.EnrollmentRepository;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ProctorAssignmentRepository proctorAssignmentRepository;

    @Mock
    private OutboxWriter outboxWriter;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(sessionService, enrollmentRepository,
                proctorAssignmentRepository, outboxWriter);
    }

    private ExamSession session(Long id, UUID publicId, UUID tenantId) {
        ExamSession session = new ExamSession();
        session.setId(id);
        session.setPublicId(publicId);
        session.setTenantId(tenantId);
        return session;
    }

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void enrollStudent_savesAndWritesOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            enrollment.setPublicId(UUID.randomUUID());
            return enrollment;
        });

        EnrollmentResponse response = enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(studentPublicId), caller);

        assertThat(response.studentPublicId()).isEqualTo(studentPublicId);
        verify(outboxWriter).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void bulkEnroll_createsNewRows_reportsAlreadyEnrolled_writesOutboxOnlyForNewRows() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        UUID alreadyEnrolledId = UUID.randomUUID();
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        Enrollment existingEnrollment = new Enrollment();
        existingEnrollment.setStudentPublicId(alreadyEnrolledId);

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any()))
                .thenReturn(List.of(existingEnrollment));
        when(enrollmentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Enrollment> toSave = invocation.getArgument(0);
            toSave.forEach(e -> e.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        BulkEnrollResponse response = enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(alreadyEnrolledId, newId1, newId2)), caller);

        assertThat(response.enrolled()).containsExactlyInAnyOrder(newId1, newId2);
        assertThat(response.alreadyEnrolled()).containsExactly(alreadyEnrolledId);
        verify(outboxWriter, times(2)).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void bulkEnroll_concurrentRaceOnSave_throwsAlreadyEnrolled_writesNoOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);
        UUID studentPublicId = UUID.randomUUID();

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(enrollmentRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(studentPublicId)), caller))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void list_returnsOnlyThisSessionsEnrollments() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        Enrollment enrollment = new Enrollment();
        enrollment.setPublicId(UUID.randomUUID());
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionId(1L)).thenReturn(List.of(enrollment));

        List<EnrollmentResponse> result = enrollmentService.list(sessionPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentPublicId()).isEqualTo(enrollment.getStudentPublicId());
    }

    @Test
    void unenroll_deletesAndWritesOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        Enrollment enrollment = new Enrollment();
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller);

        verify(enrollmentRepository).delete(enrollment);
        verify(outboxWriter).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unenroll_fromDifferentSession_throwsNotFound_doesNotDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        ExamSession otherSession = session(2L, UUID.randomUUID(), tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        Enrollment enrollment = new Enrollment();
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(otherSession);
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller))
                .isInstanceOf(EnrollmentNotFoundException.class);

        verify(enrollmentRepository, never()).delete(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unenroll_notFound_throws() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void assignProctor_savesAndWritesOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.save(any(ProctorAssignment.class))).thenAnswer(invocation -> {
            ProctorAssignment assignment = invocation.getArgument(0);
            assignment.setPublicId(UUID.randomUUID());
            return assignment;
        });

        ProctorAssignmentResponse response = enrollmentService.assignProctor(sessionPublicId,
                new AssignProctorRequest(proctorPublicId, null), caller);

        assertThat(response.proctorPublicId()).isEqualTo(proctorPublicId);
        assertThat(response.role()).isEqualTo(ProctorRole.ASSISTANT_PROCTOR);
        verify(outboxWriter).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void listProctors_returnsOnlyThisSessionsAssignments() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findBySessionId(1L)).thenReturn(List.of(assignment));

        List<ProctorAssignmentResponse> result = enrollmentService.listProctors(sessionPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).proctorPublicId()).isEqualTo(assignment.getProctorPublicId());
    }

    @Test
    void unassignProctor_deletesAndWritesOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(session);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));

        enrollmentService.unassignProctor(sessionPublicId, assignmentPublicId, caller);

        verify(proctorAssignmentRepository).delete(assignment);
        verify(outboxWriter).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unassignProctor_fromDifferentSession_throwsNotFound_doesNotDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        ExamSession otherSession = session(2L, UUID.randomUUID(), tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(otherSession);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> enrollmentService.unassignProctor(sessionPublicId, assignmentPublicId, caller))
                .isInstanceOf(ProctorAssignmentNotFoundException.class);

        verify(proctorAssignmentRepository, never()).delete(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void unassignProctor_notFound_throws() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.unassignProctor(sessionPublicId, assignmentPublicId, caller))
                .isInstanceOf(ProctorAssignmentNotFoundException.class);
    }

    @Test
    void updateProctorRole_updatesAndWritesOutbox() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(session);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));
        when(proctorAssignmentRepository.save(assignment)).thenReturn(assignment);

        ProctorAssignmentResponse response = enrollmentService.updateProctorRole(sessionPublicId, assignmentPublicId,
                new UpdateProctorRoleRequest(ProctorRole.LEAD_PROCTOR), caller);

        assertThat(response.role()).isEqualTo(ProctorRole.LEAD_PROCTOR);
        assertThat(assignment.getRole()).isEqualTo(ProctorRole.LEAD_PROCTOR);
        verify(outboxWriter).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void updateProctorRole_fromDifferentSession_throwsNotFound_doesNotSave() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        ExamSession otherSession = session(2L, UUID.randomUUID(), tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(otherSession);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> enrollmentService.updateProctorRole(sessionPublicId, assignmentPublicId,
                new UpdateProctorRoleRequest(ProctorRole.LEAD_PROCTOR), caller))
                .isInstanceOf(ProctorAssignmentNotFoundException.class);

        verify(proctorAssignmentRepository, never()).save(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }
}
