package com.pte.session.internal.service.attendance;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.internal.exception.attendance.AttendanceConflictException;
import com.pte.session.internal.exception.attendance.AttendanceIdempotencyConflictException;
import com.pte.session.internal.exception.attendance.AttendanceReasonRequiredException;
import com.pte.session.internal.exception.attendance.AttendanceWindowClosedException;
import com.pte.session.internal.exception.enrollment.EnrollmentNotFoundException;
import com.pte.session.internal.exception.staff.ProctorNotAssignedException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.exception.DomainException;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private ExamRegistrationRepository registrationRepository;
    @Mock
    private ExamSessionProctorRepository proctorRepository;
    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private AuditLogChangeWriter auditWriter;

    private AttendanceCommandService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID studentPublicId = UUID.randomUUID();
    private final UUID proctorId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID requestPublicId = UUID.randomUUID();

    private ExamSession session;
    private ExamRegistration registration;
    private CurrentUser proctor;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        AttendanceAccess access = new AttendanceAccess(sessionRepository, proctorRepository);
        service = new AttendanceCommandService(access, new AttendanceWindowPolicy(),
                new AttendanceReceiptStore(idempotencyKeyService, JsonMapper.builder().build()),
                new AttendanceSnapshotFactory(), registrationRepository, auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));

        session = new ExamSession();
        session.setId(100L);
        session.setPublicId(sessionPublicId);
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.OPEN);
        session.setOpensAt(NOW.minusSeconds(3600));
        session.setClosesAt(NOW.plusSeconds(3600));

        registration = new ExamRegistration();
        registration.setId(200L);
        registration.setPublicId(UUID.randomUUID());
        registration.setSession(session);
        registration.setStudentPublicId(studentPublicId);
        registration.setTenantId(tenantId);
        registration.setStatus(AudienceMemberStatus.ENROLLED);

        proctor = new CurrentUser(proctorId, tenantId, List.of("PROCTOR"));
        host = new CurrentUser(hostId, tenantId, List.of("HOST_ADMIN"));
    }

    private void stubSessionAndRegistration() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(registrationRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(registration));
    }

    private void stubNewCommand() {
        when(idempotencyKeyService.begin(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId), anyString(),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
    }

    private RecordAttendanceRequest request(AttendanceStatus status, long expectedVersion, String note) {
        return new RecordAttendanceRequest(status, registration.getPublicId(), expectedVersion, requestPublicId,
                note);
    }

    @Test
    void hostChange_updatesTheRegistration_writesAuditHistory_andStoresTheReceipt() {
        stubSessionAndRegistration();
        stubNewCommand();

        AttendanceCommandReceiptView receipt = service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, "  arrived on time  "), host, true);

        assertThat(registration.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(registration.getAttendanceVersion()).isEqualTo(1L);
        assertThat(registration.getLastActorPublicId()).isEqualTo(hostId);
        assertThat(registration.getLastActorRole()).isEqualTo("HOST_ADMIN");
        assertThat(registration.getLastReasonOrNote()).isEqualTo("arrived on time");
        assertThat(receipt.changed()).isTrue();
        assertThat(receipt.committedResult()).isEqualTo("SUCCESS");
        assertThat(receipt.appliedVersion()).isEqualTo(1L);
        assertThat(receipt.previousStatus()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(receipt.currentAttendance().version()).isEqualTo(1L);
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditWriter).record(eq(host), audit.capture());
        assertThat(audit.getValue().aggregateId()).isEqualTo(sessionPublicId + ":" + studentPublicId);
        assertThat(audit.getValue().requestPublicId()).isEqualTo(requestPublicId);
        assertThat(audit.getValue().beforeStateJson()).contains("UNMARKED");
        assertThat(audit.getValue().afterStateJson()).contains("PRESENT");
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId),
                anyString());
        // Receipts live until 90 days after the session closes: the key is anchored on closesAt.
        verify(idempotencyKeyService).begin(eq(IdempotencyScope.ATTENDANCE),
                eq("ATTENDANCE:" + sessionPublicId + ":" + hostId + ":" + requestPublicId), eq(tenantId),
                anyString(), eq(session.getClosesAt()));
    }

    @Test
    void sameStatus_isAReceiptedNoOp_withoutAuditOrVersionChange() {
        registration.setAttendanceStatus(AttendanceStatus.PRESENT);
        registration.setAttendanceVersion(4L);
        stubSessionAndRegistration();
        stubNewCommand();

        AttendanceCommandReceiptView receipt = service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 4L, "still present"), host, true);

        assertThat(receipt.changed()).isFalse();
        assertThat(receipt.committedResult()).isEqualTo("NO_OP");
        assertThat(receipt.appliedVersion()).isEqualTo(4L);
        assertThat(registration.getAttendanceVersion()).isEqualTo(4L);
        verify(auditWriter, never()).record(any(), any());
        verify(registrationRepository, never()).save(any());
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId),
                anyString());
    }

    @Test
    void replayOfAnIdenticalCommand_returnsTheStoredReceiptWithAFreshSnapshot() {
        stubSessionAndRegistration();
        registration.setAttendanceStatus(AttendanceStatus.LATE);
        registration.setAttendanceVersion(7L);
        String stored = "{\"requestPublicId\":\"" + requestPublicId + "\",\"sessionPublicId\":\"" + sessionPublicId
                + "\",\"studentPublicId\":\"" + studentPublicId + "\",\"registrationPublicId\":\""
                + registration.getPublicId() + "\",\"actorPublicId\":\"" + hostId
                + "\",\"actorRole\":\"HOST_ADMIN\",\"appliedVersion\":6,\"previousStatus\":\"UNMARKED\","
                + "\"newStatus\":\"PRESENT\",\"reasonOrNote\":\"first\",\"serverActionAt\":\"2026-10-13T09:00:00Z\","
                + "\"changed\":true,\"committedResult\":\"SUCCESS\"}";
        when(idempotencyKeyService.begin(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId), anyString(),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.REPLAY, stored));

        AttendanceCommandReceiptView receipt = service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 5L, "first"), host, true);

        assertThat(receipt.appliedVersion()).isEqualTo(6L);
        assertThat(receipt.newStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(receipt.currentAttendance().status()).isEqualTo(AttendanceStatus.LATE);
        assertThat(receipt.currentAttendance().version()).isEqualTo(7L);
        verify(auditWriter, never()).record(any(), any());
        verify(idempotencyKeyService, never()).complete(any(), anyString(), any(), anyString());
    }

    @Test
    void reusedRequestIdWithADifferentIntent_isRejected() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(idempotencyKeyService.begin(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId), anyString(),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null));

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.ABSENT, 0L, "different"), host, true))
                .isInstanceOf(AttendanceIdempotencyConflictException.class);
    }

    @Test
    void staleExpectedVersion_isAConflict() {
        registration.setAttendanceVersion(3L);
        stubSessionAndRegistration();
        stubNewCommand();

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 2L, "late edit"), host, true))
                .isInstanceOf(AttendanceConflictException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getUserMessage()).contains("expected 2 but current version is 3"));
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void commandForAnotherRegistrationId_isAConflict() {
        stubSessionAndRegistration();
        stubNewCommand();
        RecordAttendanceRequest wrong = new RecordAttendanceRequest(AttendanceStatus.PRESENT, UUID.randomUUID(), 0L,
                requestPublicId, "note");

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId, wrong, host, true))
                .isInstanceOf(AttendanceConflictException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getUserMessage()).contains("Enrollment generation mismatch"));
    }

    @Test
    void studentWithoutAnEnrolledRegistration_isNotFound() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(registrationRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.empty());
        stubNewCommand();

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, "note"), host, true))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void hostWithoutAReason_isRejected() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        stubNewCommand();

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, "   "), host, true))
                .isInstanceOf(AttendanceReasonRequiredException.class);
    }

    @Test
    void proctorChangingAMarkedStatusWithoutAReason_isRejected() {
        registration.setAttendanceStatus(AttendanceStatus.PRESENT);
        registration.setAttendanceVersion(1L);
        stubSessionAndRegistration();
        stubNewCommand();
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(true);

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.ABSENT, 1L, null), proctor, false))
                .isInstanceOf(AttendanceReasonRequiredException.class);
    }

    @Test
    void proctorFirstMarkNeedsNoReason() {
        stubSessionAndRegistration();
        stubNewCommand();
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(true);

        AttendanceCommandReceiptView receipt = service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, null), proctor, false);

        assertThat(receipt.actorRole()).isEqualTo("PROCTOR");
        assertThat(registration.getAttendanceStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void proctorOfAnotherSession_isRejectedBeforeAnyLookup() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, null), proctor, false))
                .isInstanceOf(ProctorNotAssignedException.class);
        verify(idempotencyKeyService, never()).begin(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void proctorBeforeTheLeadWindow_isRejected() {
        session.setOpensAt(NOW.plusSeconds(3600));
        session.setClosesAt(NOW.plusSeconds(7200));
        session.setStatus(SessionStatus.SCHEDULED);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(true);
        stubNewCommand();

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, null), proctor, false))
                .isInstanceOf(AttendanceWindowClosedException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getUserMessage()).isEqualTo("Attendance recording is not permitted before 30 minutes prior to session opening"));
    }

    @Test
    void cancelledSession_isRejectedForTheHostToo() {
        session.setStatus(SessionStatus.CANCELLED);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        stubNewCommand();

        assertThatThrownBy(() -> service.recordAttendance(sessionPublicId, studentPublicId,
                request(AttendanceStatus.PRESENT, 0L, "note"), host, true))
                .isInstanceOf(AttendanceWindowClosedException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getUserMessage()).isEqualTo("Cannot record attendance for a cancelled session"));
    }
}
