package com.pte.session.internal.service;

import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ProctorAssignment;
import com.pte.session.domain.enums.ProctorRole;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.AssignProctorRequest;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.request.EnrollStudentRequest;
import com.pte.session.internal.dto.request.UpdateProctorRoleRequest;
import com.pte.session.internal.dto.response.BulkEnrollResponse;
import com.pte.session.internal.dto.response.EnrollmentResponse;
import com.pte.session.internal.dto.response.ProctorAssignmentResponse;
import com.pte.session.internal.dto.response.StudentEnrollmentResponse;
import com.pte.session.internal.exception.AlreadyEnrolledException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.exception.ProctorAssignmentNotFoundException;
import com.pte.session.internal.exception.SessionCapacityExceededException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ported from services/scheduling's own EnrollmentServiceTest (plans/modular-monolith
 * Phase 06) — same coverage, minus outbox verification (no OutboxWriter in the
 * monolith; StudentEnrolled/ProctorAssigned etc. become plain in-process state
 * changes with nothing to observe on the wire).
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ProctorAssignmentRepository proctorAssignmentRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(sessionLifecycleService, enrollmentRepository,
                proctorAssignmentRepository, eventPublisher);
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
    void enrollStudent_saves() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            enrollment.setPublicId(UUID.randomUUID());
            return enrollment;
        });

        EnrollmentResponse response = enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(studentPublicId), caller);

        assertThat(response.studentPublicId()).isEqualTo(studentPublicId);
    }

    @Test
    void enrollStudent_rejectsWhenSessionCapacityIsFull() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setCapacity(1);
        CurrentUser caller = hostAdmin(tenantId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.countBySessionId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(UUID.randomUUID()), caller))
                .isInstanceOf(SessionCapacityExceededException.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void bulkEnroll_createsNewRows_reportsAlreadyEnrolled() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        UUID alreadyEnrolledId = UUID.randomUUID();
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        Enrollment existingEnrollment = new Enrollment();
        existingEnrollment.setStudentPublicId(alreadyEnrolledId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
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
        // Regression: capacity == null (unset here) must skip the capacity
        // check entirely, not just "pass" with a 0/default count.
        verify(enrollmentRepository, never()).countBySessionId(anyLong());
    }

    @Test
    void bulkEnroll_concurrentRaceOnSave_throwsAlreadyEnrolled() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);
        UUID studentPublicId = UUID.randomUUID();

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(enrollmentRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(studentPublicId)), caller))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void bulkEnroll_withCapacity_exactFit_succeeds() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setCapacity(5);
        CurrentUser caller = hostAdmin(tenantId);
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(enrollmentRepository.countBySessionId(1L)).thenReturn(3L);
        when(enrollmentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Enrollment> toSave = invocation.getArgument(0);
            toSave.forEach(e -> e.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        BulkEnrollResponse response = enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(newId1, newId2)), caller);

        assertThat(response.enrolled()).containsExactlyInAnyOrder(newId1, newId2);
    }

    @Test
    void bulkEnroll_withCapacity_overflow_rejectedWithNoPartialCommit() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setCapacity(5);
        CurrentUser caller = hostAdmin(tenantId);
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        // 4 already enrolled + 2 new = 6 > capacity 5.
        when(enrollmentRepository.countBySessionId(1L)).thenReturn(4L);

        assertThatThrownBy(() -> enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(newId1, newId2)), caller))
                .isInstanceOf(SessionCapacityExceededException.class);

        verify(enrollmentRepository, never()).saveAll(any());
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

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionId(1L)).thenReturn(List.of(enrollment));

        List<EnrollmentResponse> result = enrollmentService.list(sessionPublicId, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentPublicId()).isEqualTo(enrollment.getStudentPublicId());
    }

    @Test
    void unenroll_deletes() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        Enrollment enrollment = new Enrollment();
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller);

        verify(enrollmentRepository).delete(enrollment);
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

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller))
                .isInstanceOf(EnrollmentNotFoundException.class);

        verify(enrollmentRepository, never()).delete(any());
    }

    @Test
    void assignProctor_saves() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.save(any(ProctorAssignment.class))).thenAnswer(invocation -> {
            ProctorAssignment assignment = invocation.getArgument(0);
            assignment.setPublicId(UUID.randomUUID());
            return assignment;
        });

        ProctorAssignmentResponse response = enrollmentService.assignProctor(sessionPublicId,
                new AssignProctorRequest(proctorPublicId, null), caller);

        assertThat(response.proctorPublicId()).isEqualTo(proctorPublicId);
        assertThat(response.role()).isEqualTo(ProctorRole.ASSISTANT_PROCTOR);
    }

    @Test
    void updateProctorRole_updates() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(session);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));
        when(proctorAssignmentRepository.save(assignment)).thenReturn(assignment);

        ProctorAssignmentResponse response = enrollmentService.updateProctorRole(sessionPublicId, assignmentPublicId,
                new UpdateProctorRoleRequest(ProctorRole.LEAD_PROCTOR), caller);

        assertThat(response.role()).isEqualTo(ProctorRole.LEAD_PROCTOR);
        assertThat(assignment.getRole()).isEqualTo(ProctorRole.LEAD_PROCTOR);
    }

    @Test
    void listForStudent_joinFetchesSessionDetailsInOneQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        Instant opensAt = Instant.parse("2026-10-01T00:00:00Z");
        Instant closesAt = Instant.parse("2026-10-01T02:00:00Z");
        CurrentUser caller = hostAdmin(tenantId);

        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setName("PTE Mock Test - Oct Batch");
        session.setStatus(SessionStatus.SCHEDULED);
        session.setOpensAt(opensAt);
        session.setClosesAt(closesAt);

        Enrollment enrollment = new Enrollment();
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(studentPublicId);
        enrollment.setTenantId(tenantId);

        when(enrollmentRepository.findByStudentPublicIdAndTenantId(studentPublicId, tenantId))
                .thenReturn(List.of(enrollment));

        List<StudentEnrollmentResponse> result = enrollmentService.listForStudent(studentPublicId, caller);

        assertThat(result).hasSize(1);
        StudentEnrollmentResponse response = result.get(0);
        assertThat(response.enrollmentPublicId()).isEqualTo(enrollmentPublicId);
        assertThat(response.sessionName()).isEqualTo("PTE Mock Test - Oct Batch");
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

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorAssignmentRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> enrollmentService.updateProctorRole(sessionPublicId, assignmentPublicId,
                new UpdateProctorRoleRequest(ProctorRole.LEAD_PROCTOR), caller))
                .isInstanceOf(ProctorAssignmentNotFoundException.class);

        verify(proctorAssignmentRepository, never()).save(any());
    }

    @Test
    void enrollStudent_publishesStudentEnrolledEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            enrollment.setPublicId(UUID.randomUUID());
            return enrollment;
        });

        enrollmentService.enrollStudent(sessionPublicId, new EnrollStudentRequest(studentPublicId), caller);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();

        // Verify it's a StudentEnrolledEvent with correct fields
        assertThat(event).isInstanceOf(com.pte.session.dto.event.StudentEnrolledEvent.class);
        var enrollmentEvent = (com.pte.session.dto.event.StudentEnrolledEvent) event;
        assertThat(enrollmentEvent.studentPublicId()).isEqualTo(studentPublicId);
        assertThat(enrollmentEvent.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(enrollmentEvent.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void bulkEnroll_publishesOneStudentEnrolledEventPerNewEnrollment() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        UUID alreadyEnrolledId = UUID.randomUUID();
        UUID newId1 = UUID.randomUUID();
        UUID newId2 = UUID.randomUUID();

        Enrollment existingEnrollment = new Enrollment();
        existingEnrollment.setStudentPublicId(alreadyEnrolledId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(enrollmentRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any()))
                .thenReturn(List.of(existingEnrollment));
        when(enrollmentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Enrollment> toSave = invocation.getArgument(0);
            toSave.forEach(e -> e.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        enrollmentService.bulkEnroll(sessionPublicId, new BulkEnrollRequest(List.of(alreadyEnrolledId, newId1, newId2)), caller);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        var events = captor.getAllValues();

        // Verify exactly 2 StudentEnrolledEvents were published (one per newly enrolled, not for already-enrolled)
        var enrollmentEvents = events.stream()
                .filter(e -> e instanceof com.pte.session.dto.event.StudentEnrolledEvent)
                .map(e -> (com.pte.session.dto.event.StudentEnrolledEvent) e)
                .toList();
        assertThat(enrollmentEvents).hasSize(2);

        var studentIds = enrollmentEvents.stream().map(com.pte.session.dto.event.StudentEnrolledEvent::studentPublicId).toList();
        assertThat(studentIds).containsExactlyInAnyOrder(newId1, newId2);
    }
}
