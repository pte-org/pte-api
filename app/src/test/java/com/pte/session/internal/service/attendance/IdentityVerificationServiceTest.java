package com.pte.session.internal.service.attendance;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.IdentityVerificationStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.exception.attendance.AttendanceConflictException;
import com.pte.session.internal.exception.attendance.AttendanceReasonRequiredException;
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
import org.springframework.web.server.ResponseStatusException;
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
class IdentityVerificationServiceTest {

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

    private IdentityVerificationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID studentPublicId = UUID.randomUUID();
    private final UUID proctorId = UUID.randomUUID();
    private final UUID requestPublicId = UUID.randomUUID();

    private ExamSession session;
    private ExamRegistration registration;
    private CurrentUser proctor;

    @BeforeEach
    void setUp() {
        service = new IdentityVerificationService(new AttendanceAccess(sessionRepository, proctorRepository),
                new AttendanceWindowPolicy(),
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
        registration.setPublicId(UUID.randomUUID());
        registration.setSession(session);
        registration.setStudentPublicId(studentPublicId);
        registration.setTenantId(tenantId);
        registration.setStatus(AudienceMemberStatus.ENROLLED);
        proctor = new CurrentUser(proctorId, tenantId, List.of("PROCTOR"));
    }

    private void stubEligibleCommand() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(true);
        when(registrationRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(registration));
        when(idempotencyKeyService.begin(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId), anyString(),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
    }

    private RecordIdentityVerificationRequest request(String status, long expectedVersion, String note) {
        return new RecordIdentityVerificationRequest(status, registration.getPublicId(), expectedVersion,
                requestPublicId, note);
    }

    @Test
    void verifiedStatus_isStoredOnTheRegistration_andBumpsTheVersion() {
        registration.setAttendanceVersion(2L);
        stubEligibleCommand();

        AttendanceRowView row = service.recordIdentityVerification(sessionPublicId, studentPublicId,
                request("verified", 2L, " passport checked "), proctor, false);

        assertThat(registration.getIdentityStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
        assertThat(registration.getIdentityNote()).isEqualTo("passport checked");
        assertThat(registration.getAttendanceVersion()).isEqualTo(3L);
        assertThat(row.identityStatus()).isEqualTo("VERIFIED");
        assertThat(row.version()).isEqualTo(3L);
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditWriter).record(eq(proctor), audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("IDENTITY_VERIFIED");
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId),
                anyString());
    }

    @Test
    void unchangedStatusAndNote_isANoOp_withoutVersionBumpOrAudit() {
        registration.setIdentityStatus(IdentityVerificationStatus.VERIFIED);
        registration.setIdentityNote("checked");
        registration.setAttendanceVersion(5L);
        stubEligibleCommand();

        AttendanceRowView row = service.recordIdentityVerification(sessionPublicId, studentPublicId,
                request("VERIFIED", 5L, "checked"), proctor, false);

        assertThat(row.version()).isEqualTo(5L);
        verify(auditWriter, never()).record(any(), any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void staleExpectedVersion_isAConflict() {
        registration.setAttendanceVersion(2L);
        stubEligibleCommand();

        assertThatThrownBy(() -> service.recordIdentityVerification(sessionPublicId, studentPublicId,
                request("FAILED", 1L, null), proctor, false))
                .isInstanceOf(AttendanceConflictException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getUserMessage()).contains("Identity verification version conflict"));
    }

    @Test
    void unknownStatus_isABadRequest() {
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.recordIdentityVerification(sessionPublicId, studentPublicId,
                request("MAYBE", 0L, null), proctor, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Identity status must be one of");
    }

    @Test
    void hostOnAClosedSession_mustGiveAReason() {
        session.setStatus(SessionStatus.CLOSED);
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(idempotencyKeyService.begin(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId), anyString(),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));

        assertThatThrownBy(() -> service.recordIdentityVerification(sessionPublicId, studentPublicId,
                request("VERIFIED", 0L, " "), host, true))
                .isInstanceOf(AttendanceReasonRequiredException.class);
    }
}
