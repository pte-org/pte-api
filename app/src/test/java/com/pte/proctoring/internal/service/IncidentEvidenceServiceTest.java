package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptEvidenceContextView;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.domain.enums.LockdownViolationSeverity;
import com.pte.proctoring.domain.enums.LockdownViolationType;
import com.pte.proctoring.internal.dto.request.RecordSecurityViolationRequest;
import com.pte.proctoring.internal.dto.response.SecurityViolationReceipt;
import com.pte.proctoring.internal.exception.SecurityAuditDisabledException;
import com.pte.proctoring.internal.exception.SecurityAuditEventConflictException;
import com.pte.proctoring.internal.exception.SecurityAuditPolicyUnresolvedException;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentEvidenceServiceTest {

    @Mock
    private AttemptService attemptService;

    @Mock
    private ExamIncidentRepository incidentRepository;

    private IncidentEvidenceService service;
    private final UUID studentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(studentId, tenantId, List.of("STUDENT"));

    @BeforeEach
    void setUp() {
        service = new IncidentEvidenceService(attemptService, incidentRepository, java.time.Clock.systemUTC());
    }

    private void lockdown(String mode) {
        when(attemptService.lockOwnAttemptForEvidence(attemptId, caller)).thenReturn(
                new AttemptEvidenceContextView(attemptId, sessionId, studentId, tenantId, mode));
    }

    private void saveAssignsPublicId() {
        when(incidentRepository.save(any(ExamIncident.class))).thenAnswer(invocation -> {
            ExamIncident incident = invocation.getArgument(0);
            incident.setPublicId(UUID.randomUUID());
            return incident;
        });
    }

    @Test
    void standardPolicy_recordsAnAutoIncidentWithWarningAndServerOwnership() {
        lockdown("STANDARD");
        saveAssignsPublicId();
        Instant before = Instant.now();

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest(" event-1 ", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                        before.minusSeconds(1), "window left fullscreen"), caller);

        ArgumentCaptor<ExamIncident> captor = ArgumentCaptor.forClass(ExamIncident.class);
        verify(incidentRepository).save(captor.capture());
        ExamIncident saved = captor.getValue();
        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.severity()).isEqualTo(LockdownViolationSeverity.WARNING);
        assertThat(receipt.clientEventId()).isEqualTo("event-1");
        assertThat(saved.getOrigin()).isEqualTo(IncidentOrigin.AUTO);
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStudentPublicId()).isEqualTo(studentId);
        assertThat(saved.getSessionPublicId()).isEqualTo(sessionId);
        assertThat(saved.getAttemptPublicId()).isEqualTo(attemptId);
        assertThat(saved.getType()).isEqualTo("LOCKDOWN_FULLSCREEN_EXIT");
        assertThat(saved.getDetectedAt()).isBetween(before, Instant.now());
    }

    @Test
    void strictPolicy_derivesCritical() {
        lockdown("STRICT");
        saveAssignsPublicId();

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest("event-2", LockdownViolationType.LOCKDOWN_SHORTCUT_BLOCKED,
                        null, null), caller);

        assertThat(receipt.severity()).isEqualTo(LockdownViolationSeverity.CRITICAL);
    }

    @Test
    void nonePolicy_rejectsWithoutWriting() {
        lockdown("NONE");

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-3", LockdownViolationType.LOCKDOWN_CLIPBOARD_PASTE,
                        null, null), caller))
                .isInstanceOf(SecurityAuditDisabledException.class);

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void unknownPolicy_isUnresolvedAndWritesNothing() {
        lockdown("SOMETHING_ELSE");

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-7", LockdownViolationType.LOCKDOWN_CLIPBOARD_PASTE,
                        null, null), caller))
                .isInstanceOf(SecurityAuditPolicyUnresolvedException.class);

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void duplicateSamePayload_returnsOriginalReceiptWithoutSecondRow() {
        lockdown("STANDARD");
        ExamIncident existing = existing("event-5", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT, "same",
                Instant.parse("2026-09-28T03:00:00Z"));
        when(incidentRepository.findByAttemptPublicIdAndClientEventId(attemptId, "event-5"))
                .thenReturn(Optional.of(existing));

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest("event-5", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                        existing.getClientOccurredAt(), "same"), caller);

        assertThat(receipt.duplicate()).isTrue();
        assertThat(receipt.publicId()).isEqualTo(existing.getPublicId());
        assertThat(receipt.detectedAt()).isEqualTo(existing.getDetectedAt());
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void duplicateDifferentPayload_returnsStableConflict() {
        lockdown("STANDARD");
        ExamIncident existing = existing("event-6", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT, "original",
                Instant.now());
        when(incidentRepository.findByAttemptPublicIdAndClientEventId(attemptId, "event-6"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-6", LockdownViolationType.LOCKDOWN_SHORTCUT_BLOCKED,
                        null, "changed"), caller))
                .isInstanceOf(SecurityAuditEventConflictException.class);
        verify(incidentRepository, never()).save(any());
    }

    private ExamIncident existing(String clientEventId, LockdownViolationType type, String detail,
                                  Instant detectedAt) {
        ExamIncident incident = new ExamIncident();
        incident.setDetectedAt(java.time.Instant.now());
        incident.setPublicId(UUID.randomUUID());
        incident.setAttemptPublicId(attemptId);
        incident.setOrigin(IncidentOrigin.AUTO);
        incident.setType(type.name());
        incident.setSeverity(LockdownViolationSeverity.WARNING.name());
        incident.setDescription(detail);
        incident.setClientEventId(clientEventId);
        incident.setClientOccurredAt(detectedAt.minusSeconds(1));
        incident.setDetectedAt(detectedAt);
        return incident;
    }
}
