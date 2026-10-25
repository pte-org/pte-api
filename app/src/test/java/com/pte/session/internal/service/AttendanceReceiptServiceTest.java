package com.pte.session.internal.service;

import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceHistoryView;
import com.pte.session.internal.exception.AttendanceReceiptNotFoundException;
import com.pte.session.internal.exception.ProctorNotAssignedException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceReceiptServiceTest {

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
    private AuditHistoryReader historyReader;

    private AttendanceReceiptService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID studentPublicId = UUID.randomUUID();
    private final UUID hostId = UUID.randomUUID();
    private final UUID requestPublicId = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(hostId, tenantId, List.of("HOST_ADMIN"));

    @BeforeEach
    void setUp() {
        service = new AttendanceReceiptService(new AttendanceAccess(sessionRepository, proctorRepository),
                new AttendanceReceiptStore(idempotencyKeyService, JsonMapper.builder().build()),
                new AttendanceSnapshotFactory(), registrationRepository, historyReader,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void receipt_isReadFromTheIdempotencyStore_withTheCurrentSnapshot() {
        ExamSession session = new ExamSession();
        ExamRegistration registration = new ExamRegistration();
        registration.setPublicId(UUID.randomUUID());
        registration.setStatus(AudienceMemberStatus.ENROLLED);
        registration.setAttendanceStatus(AttendanceStatus.PRESENT);
        registration.setAttendanceVersion(1L);
        String stored = "{\"requestPublicId\":\"" + requestPublicId + "\",\"sessionPublicId\":\"" + sessionPublicId
                + "\",\"studentPublicId\":\"" + studentPublicId + "\",\"registrationPublicId\":\""
                + registration.getPublicId() + "\",\"actorPublicId\":\"" + hostId
                + "\",\"actorRole\":\"HOST_ADMIN\",\"appliedVersion\":1,\"previousStatus\":\"UNMARKED\","
                + "\"newStatus\":\"PRESENT\",\"reasonOrNote\":\"ok\",\"serverActionAt\":\"2026-10-13T09:00:00Z\","
                + "\"changed\":true,\"committedResult\":\"SUCCESS\"}";
        when(idempotencyKeyService.findResult(eq(IdempotencyScope.ATTENDANCE),
                eq("ATTENDANCE:" + sessionPublicId + ":" + hostId + ":" + requestPublicId), eq(tenantId)))
                .thenReturn(Optional.of(stored));
        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(registrationRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(registration));

        AttendanceCommandReceiptView receipt = service.getReceipt(sessionPublicId, requestPublicId, host, true);

        assertThat(receipt.newStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(receipt.currentAttendance().version()).isEqualTo(1L);
    }

    @Test
    void unknownReceipt_isNotFound() {
        when(idempotencyKeyService.findResult(eq(IdempotencyScope.ATTENDANCE), anyString(), eq(tenantId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReceipt(sessionPublicId, requestPublicId, host, true))
                .isInstanceOf(AttendanceReceiptNotFoundException.class);
    }

    @Test
    void receiptOfAnotherSessionsProctor_isRejectedBeforeLookup() {
        UUID proctorId = UUID.randomUUID();
        CurrentUser proctor = new CurrentUser(proctorId, tenantId, List.of("PROCTOR"));
        when(proctorRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.getReceipt(sessionPublicId, requestPublicId, proctor, false))
                .isInstanceOf(ProctorNotAssignedException.class);
    }

    @Test
    void history_isBuiltFromTheAuditTrail_newestFirst() {
        UUID registrationId = UUID.randomUUID();
        String before = "{\"studentPublicId\":\"" + studentPublicId + "\",\"registrationPublicId\":\""
                + registrationId + "\",\"status\":\"UNMARKED\",\"version\":0,\"reasonOrNote\":null,"
                + "\"actorRole\":\"HOST_ADMIN\"}";
        String after = "{\"studentPublicId\":\"" + studentPublicId + "\",\"registrationPublicId\":\""
                + registrationId + "\",\"status\":\"ABSENT\",\"version\":1,\"reasonOrNote\":\"no show\","
                + "\"actorRole\":\"HOST_ADMIN\"}";
        AuditHistoryEntry entry = new AuditHistoryEntry(UUID.randomUUID(), hostId, "HOST_ADMIN", requestPublicId,
                "Attendance UNMARKED -> ABSENT", before, after, NOW);
        when(historyReader.history(tenantId, "SESSION_ATTENDANCE", sessionPublicId + ":" + studentPublicId,
                "ATTENDANCE_RECORDED", 0, 20)).thenReturn(new PagedResult<>(List.of(entry),
                        new PageMeta(0, 20, 1, 1, true, true, false, false)));

        AttendanceHistoryView history = service.getHistory(sessionPublicId, studentPublicId, 0, 20, host, true);

        assertThat(history.data()).hasSize(1);
        var item = history.data().get(0);
        assertThat(item.previousStatus()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(item.newStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(item.appliedVersion()).isEqualTo(1L);
        assertThat(item.enrollmentPublicId()).isEqualTo(registrationId);
        assertThat(item.reasonOrNote()).isEqualTo("no show");
        assertThat(item.actorPublicId()).isEqualTo(hostId);
        assertThat(item.serverActionAt()).isEqualTo(NOW);
    }
}
