package com.pte.session.internal.service;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.StudentEnrollmentConflictEvent;
import com.pte.session.dto.response.SlotConflict;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.request.EnrollStudentRequest;
import com.pte.session.internal.dto.response.BulkEnrollResponse;
import com.pte.session.internal.dto.response.EnrollmentResponse;
import com.pte.session.internal.dto.response.StudentEnrollmentResponse;
import com.pte.session.internal.exception.AlreadyEnrolledException;
import com.pte.session.internal.exception.EnrolledStudentAlreadyStartedException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.exception.ExamAudienceLockedException;
import com.pte.session.internal.exception.SessionCapacityExceededException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.identity.IdentityService;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
    private ExamRegistrationRepository registrationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private IdentityService identityService;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private StartedAttemptLookup startedAttemptLookup;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(sessionLifecycleService, registrationRepository,
                eventPublisher, tenancyService, identityService, startedAttemptLookup);
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
        when(registrationRepository.save(any(ExamRegistration.class))).thenAnswer(invocation -> {
            ExamRegistration enrollment = invocation.getArgument(0);
            enrollment.setPublicId(UUID.randomUUID());
            return enrollment;
        });

        EnrollmentResponse response = enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(studentPublicId), caller);

        assertThat(response.studentPublicId()).isEqualTo(studentPublicId);
        verify(identityService).lockActiveStudentsInTenant(List.of(studentPublicId), tenantId);
    }

    @Test
    void enrollStudent_rejectsIneligibleIdentityBeforeCheckingEnrollment() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        doThrow(new IdentityService.IdentityNotEligibleException()).when(identityService)
                .requireActiveStudentInTenant(studentPublicId, tenantId);

        assertThatThrownBy(() -> enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(studentPublicId), caller))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);

        verify(registrationRepository, never()).findBySessionIdAndStudentPublicIdIn(anyLong(), any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void enrollStudent_rejectsWhenSessionCapacityIsFull() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setCapacity(1);
        CurrentUser caller = hostAdmin(tenantId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.countBySessionIdAndStatus(eq(1L), eq(AudienceMemberStatus.ENROLLED))).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(UUID.randomUUID()), caller))
                .isInstanceOf(SessionCapacityExceededException.class);
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void enrollStudent_rejectsOverlappingExistingSession() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        UUID conflictingSessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setOpensAt(Instant.parse("2026-10-17T08:00:00Z"));
        session.setClosesAt(Instant.parse("2026-10-17T10:00:00Z"));
        CurrentUser caller = hostAdmin(tenantId);
        ExamRegistrationRepository.SlotConflictProjection conflict = org.mockito.Mockito.mock(
                ExamRegistrationRepository.SlotConflictProjection.class);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findConflictingEnrollments(eq(tenantId), eq(List.of(studentPublicId)),
                eq(session.getOpensAt()), eq(session.getClosesAt()))).thenReturn(List.of(conflict));
        when(conflict.getStudentPublicId()).thenReturn(studentPublicId);
        when(conflict.getConflictingSessionPublicId()).thenReturn(conflictingSessionPublicId);

        assertThatThrownBy(() -> enrollmentService.enrollStudent(sessionPublicId,
                new EnrollStudentRequest(studentPublicId), caller))
                .isInstanceOf(SessionTimeConflictException.class);
        verify(registrationRepository, never()).save(any());
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

        ExamRegistration existingEnrollment = new ExamRegistration();
        existingEnrollment.setStatus(AudienceMemberStatus.ENROLLED);
        existingEnrollment.setStudentPublicId(alreadyEnrolledId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any()))
                .thenReturn(List.of(existingEnrollment));
        when(registrationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ExamRegistration> toSave = invocation.getArgument(0);
            toSave.forEach(e -> e.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        BulkEnrollResponse response = enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(alreadyEnrolledId, newId1, newId2)), caller);

        assertThat(response.enrolled()).containsExactlyInAnyOrder(newId1, newId2);
        assertThat(response.alreadyEnrolled()).containsExactly(alreadyEnrolledId);
        verify(identityService).lockActiveStudentsInTenant(
                List.of(alreadyEnrolledId, newId1, newId2), tenantId);
        // Regression: capacity == null (unset here) must skip the capacity
        // check entirely, not just "pass" with a 0/default count.
        verify(registrationRepository, never()).countBySessionIdAndStatus(anyLong(), any());
    }

    @Test
    void bulkEnroll_skipsOverlappingStudents_andChecksCapacityAfterFiltering() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID conflictingStudentId = UUID.randomUUID();
        UUID eligibleStudentId = UUID.randomUUID();
        UUID conflictingSessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setName("October morning exam");
        session.setOpensAt(Instant.parse("2026-10-17T08:00:00Z"));
        session.setClosesAt(Instant.parse("2026-10-17T10:00:00Z"));
        session.setCapacity(2);
        CurrentUser caller = hostAdmin(tenantId);
        ExamRegistrationRepository.SlotConflictProjection conflict = org.mockito.Mockito.mock(
                ExamRegistrationRepository.SlotConflictProjection.class);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any()))
                .thenReturn(List.of());
        when(registrationRepository.findConflictingEnrollments(eq(tenantId),
                eq(List.of(conflictingStudentId, eligibleStudentId)), eq(session.getOpensAt()),
                eq(session.getClosesAt()))).thenReturn(List.of(conflict));
        when(conflict.getStudentPublicId()).thenReturn(conflictingStudentId);
        when(conflict.getConflictingSessionPublicId()).thenReturn(conflictingSessionPublicId);
        when(conflict.getConflictingSessionName()).thenReturn("Earlier exam");
        when(registrationRepository.countBySessionIdAndStatus(eq(1L), eq(AudienceMemberStatus.ENROLLED))).thenReturn(1L);
        when(registrationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ExamRegistration> toSave = invocation.getArgument(0);
            toSave.forEach(e -> e.setPublicId(UUID.randomUUID()));
            return toSave;
        });

        BulkEnrollResponse response = enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(conflictingStudentId, eligibleStudentId)), caller);

        assertThat(response.enrolled()).containsExactly(eligibleStudentId);
        assertThat(response.skippedByConflict()).containsExactly(
                new SlotConflict(conflictingStudentId, conflictingSessionPublicId, "Earlier exam"));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).anyMatch(event -> event instanceof StudentEnrollmentConflictEvent);
    }

    @Test
    void bulkEnroll_concurrentRaceOnSave_throwsAlreadyEnrolled() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);
        UUID studentPublicId = UUID.randomUUID();

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(registrationRepository.saveAll(any()))
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
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(registrationRepository.countBySessionIdAndStatus(eq(1L), eq(AudienceMemberStatus.ENROLLED))).thenReturn(3L);
        when(registrationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ExamRegistration> toSave = invocation.getArgument(0);
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
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        // 4 already enrolled + 2 new = 6 > capacity 5.
        when(registrationRepository.countBySessionIdAndStatus(eq(1L), eq(AudienceMemberStatus.ENROLLED))).thenReturn(4L);

        assertThatThrownBy(() -> enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(newId1, newId2)), caller))
                .isInstanceOf(SessionCapacityExceededException.class);

        verify(registrationRepository, never()).saveAll(any());
    }

    @Test
    void list_returnsOnlyThisSessionsEnrollments() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setPublicId(UUID.randomUUID());
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStatus(1L, AudienceMemberStatus.ENROLLED)).thenReturn(List.of(enrollment));

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

        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller);

        verify(registrationRepository).delete(enrollment);
    }

    @Test
    void unenroll_fromDifferentSession_throwsNotFound_doesNotDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        ExamSession otherSession = session(2L, UUID.randomUUID(), tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(otherSession);
        enrollment.setStudentPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller))
                .isInstanceOf(EnrollmentNotFoundException.class);

        verify(registrationRepository, never()).delete(any());
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

        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(studentPublicId);
        enrollment.setTenantId(tenantId);

        when(registrationRepository.findEnrollmentsOfStudent(studentPublicId, tenantId))
                .thenReturn(List.of(enrollment));

        List<StudentEnrollmentResponse> result = enrollmentService.listForStudent(studentPublicId, caller);

        assertThat(result).hasSize(1);
        StudentEnrollmentResponse response = result.get(0);
        assertThat(response.enrollmentPublicId()).isEqualTo(enrollmentPublicId);
        assertThat(response.sessionName()).isEqualTo("PTE Mock Test - Oct Batch");
    }

    @Test
    void enrollStudent_publishesStudentEnrolledEvent() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.save(any(ExamRegistration.class))).thenAnswer(invocation -> {
            ExamRegistration enrollment = invocation.getArgument(0);
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
    void bulkEnroll_afterExamClosed_enrollsWithoutNotifying() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setStatus(SessionStatus.CLOSED);
        CurrentUser caller = hostAdmin(tenantId);
        UUID newId = UUID.randomUUID();

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any())).thenReturn(List.of());
        when(registrationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BulkEnrollResponse response = enrollmentService.bulkEnroll(sessionPublicId,
                new BulkEnrollRequest(List.of(newId)), caller);

        assertThat(response.enrolled()).containsExactly(newId);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void bulkEnroll_whileExamIsNotScheduledOrClosed_isLocked() {
        for (SessionStatus status : List.of(SessionStatus.DRAFT, SessionStatus.READY,
                SessionStatus.OPEN, SessionStatus.CANCELLED)) {
            UUID tenantId = UUID.randomUUID();
            UUID sessionPublicId = UUID.randomUUID();
            ExamSession session = session(1L, sessionPublicId, tenantId);
            session.setStatus(status);
            CurrentUser caller = hostAdmin(tenantId);
            when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);

            assertThatThrownBy(() -> enrollmentService.bulkEnroll(sessionPublicId,
                    new BulkEnrollRequest(List.of(UUID.randomUUID())), caller))
                    .as(status.name())
                    .isInstanceOf(ExamAudienceLockedException.class);
        }
        verify(registrationRepository, never()).saveAll(any());
    }

    @Test
    void unenroll_whileExamIsOpen_isLocked() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setStatus(SessionStatus.OPEN);
        CurrentUser caller = hostAdmin(tenantId);
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, UUID.randomUUID(), caller))
                .isInstanceOf(ExamAudienceLockedException.class);

        verify(registrationRepository, never()).delete(any());
    }

    @Test
    void unenroll_studentWhoStartedTheExam_isRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID enrollmentPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        session.setStatus(SessionStatus.CLOSED);
        CurrentUser caller = hostAdmin(tenantId);

        ExamRegistration enrollment = new ExamRegistration();
        enrollment.setStatus(AudienceMemberStatus.ENROLLED);
        enrollment.setPublicId(enrollmentPublicId);
        enrollment.setSession(session);
        enrollment.setStudentPublicId(studentPublicId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findByPublicId(enrollmentPublicId)).thenReturn(Optional.of(enrollment));
        when(startedAttemptLookup.findStartedStudentPublicIds(tenantId, List.of(sessionPublicId),
                List.of(studentPublicId))).thenReturn(Set.of(studentPublicId));

        assertThatThrownBy(() -> enrollmentService.unenroll(sessionPublicId, enrollmentPublicId, caller))
                .isInstanceOf(EnrolledStudentAlreadyStartedException.class);

        verify(registrationRepository, never()).delete(any());
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

        ExamRegistration existingEnrollment = new ExamRegistration();
        existingEnrollment.setStatus(AudienceMemberStatus.ENROLLED);
        existingEnrollment.setStudentPublicId(alreadyEnrolledId);

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(registrationRepository.findBySessionIdAndStudentPublicIdIn(eq(1L), any()))
                .thenReturn(List.of(existingEnrollment));
        when(registrationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ExamRegistration> toSave = invocation.getArgument(0);
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
