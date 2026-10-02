package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptSecurityEvent;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.enums.LockdownViolationSeverity;
import com.pte.attempt.domain.enums.LockdownViolationType;
import com.pte.attempt.internal.dto.request.RecordSecurityViolationRequest;
import com.pte.attempt.internal.dto.response.SecurityViolationReceipt;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.SecurityAuditDisabledException;
import com.pte.attempt.internal.exception.SecurityAuditEventConflictException;
import com.pte.attempt.internal.repository.AttemptSecurityEventRepository;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptSecurityAuditServiceTest {

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private AttemptSecurityEventRepository securityEventRepository;

    private AttemptSecurityAuditService service;
    private final UUID studentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttemptSecurityAuditService(examAttemptRepository, securityEventRepository);
    }

    @Test
    void standardPolicy_derivesWarningAndServerOwnershipAndTimestamp() {
        ExamAttempt attempt = attempt("STANDARD");
        stubOwnedAttempt(attempt);
        when(securityEventRepository.save(any(AttemptSecurityEvent.class))).thenAnswer(invocation -> {
            AttemptSecurityEvent event = invocation.getArgument(0);
            event.setPublicId(UUID.randomUUID());
            return event;
        });
        Instant before = Instant.now();

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest(" event-1 ", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                        before.minusSeconds(1), "window left fullscreen"),
                caller(tenantId));

        Instant after = Instant.now();
        ArgumentCaptor<AttemptSecurityEvent> captor = ArgumentCaptor.forClass(AttemptSecurityEvent.class);
        verify(securityEventRepository).save(captor.capture());
        AttemptSecurityEvent saved = captor.getValue();

        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.severity()).isEqualTo(LockdownViolationSeverity.WARNING);
        assertThat(receipt.clientEventId()).isEqualTo("event-1");
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStudentPublicId()).isEqualTo(studentId);
        assertThat(saved.getSessionPublicId()).isEqualTo(sessionId);
        assertThat(saved.getDetectedAt()).isBetween(before, after);
    }

    @Test
    void strictPolicy_derivesCritical() {
        ExamAttempt attempt = attempt("STRICT");
        stubOwnedAttempt(attempt);
        when(securityEventRepository.save(any(AttemptSecurityEvent.class))).thenAnswer(invocation -> {
            AttemptSecurityEvent event = invocation.getArgument(0);
            event.setPublicId(UUID.randomUUID());
            return event;
        });

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest("event-2", LockdownViolationType.LOCKDOWN_SHORTCUT_BLOCKED,
                        null, null),
                caller(tenantId));

        assertThat(receipt.severity()).isEqualTo(LockdownViolationSeverity.CRITICAL);
    }

    @Test
    void nonePolicy_rejectsWithoutWriting() {
        ExamAttempt attempt = attempt("NONE");
        stubOwnedAttempt(attempt);

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-3", LockdownViolationType.LOCKDOWN_CLIPBOARD_PASTE,
                        null, null),
                caller(tenantId)))
                .isInstanceOf(SecurityAuditDisabledException.class);

        verify(securityEventRepository, never()).save(any());
    }

    @Test
    void wrongTenantOrStudent_isNotFoundAndCannotWrite() {
        ExamAttempt attempt = attempt("STANDARD");
        when(examAttemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptId, studentId))
                .thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-4", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                        null, null),
                caller(UUID.randomUUID())))
                .isInstanceOf(AttemptNotFoundException.class);
        verify(examAttemptRepository, never()).findWithLockById(any());
        verify(securityEventRepository, never()).save(any());
    }

    @Test
    void duplicateSamePayload_returnsOriginalReceiptWithoutSecondRow() {
        ExamAttempt attempt = attempt("STANDARD");
        stubOwnedAttempt(attempt);
        AttemptSecurityEvent existing = existingEvent(attempt, "event-5", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                "same", Instant.parse("2026-09-28T03:00:00Z"));
        when(securityEventRepository.findByAttemptIdAndClientEventId(11L, "event-5"))
                .thenReturn(Optional.of(existing));

        SecurityViolationReceipt receipt = service.record(attemptId,
                new RecordSecurityViolationRequest("event-5", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                        existing.getClientOccurredAt(), "same"),
                caller(tenantId));

        assertThat(receipt.duplicate()).isTrue();
        assertThat(receipt.publicId()).isEqualTo(existing.getPublicId());
        assertThat(receipt.detectedAt()).isEqualTo(existing.getDetectedAt());
        verify(securityEventRepository, never()).save(any());
    }

    @Test
    void duplicateDifferentPayload_returnsStableConflict() {
        ExamAttempt attempt = attempt("STANDARD");
        stubOwnedAttempt(attempt);
        AttemptSecurityEvent existing = existingEvent(attempt, "event-6", LockdownViolationType.LOCKDOWN_FULLSCREEN_EXIT,
                "original", Instant.now());
        when(securityEventRepository.findByAttemptIdAndClientEventId(11L, "event-6"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.record(attemptId,
                new RecordSecurityViolationRequest("event-6", LockdownViolationType.LOCKDOWN_SHORTCUT_BLOCKED,
                        null, "changed"),
                caller(tenantId)))
                .isInstanceOf(SecurityAuditEventConflictException.class);
        verify(securityEventRepository, never()).save(any());
    }

    private void stubOwnedAttempt(ExamAttempt attempt) {
        when(examAttemptRepository.findWithPinnedByPublicIdAndStudentPublicId(attemptId, studentId))
                .thenReturn(Optional.of(attempt));
        when(examAttemptRepository.findWithLockById(11L)).thenReturn(Optional.of(attempt));
    }

    private ExamAttempt attempt(String lockdownMode) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(11L);
        attempt.setPublicId(attemptId);
        attempt.setSessionPublicId(sessionId);
        attempt.setStudentPublicId(studentId);
        attempt.setTenantId(tenantId);
        PinnedExamSnapshot pinned = new PinnedExamSnapshot();
        pinned.setLockdownMode(lockdownMode);
        pinned.setAttempt(attempt);
        attempt.setPinnedSnapshot(pinned);
        return attempt;
    }

    private AttemptSecurityEvent existingEvent(ExamAttempt attempt, String clientEventId,
                                               LockdownViolationType type, String detail, Instant detectedAt) {
        AttemptSecurityEvent event = new AttemptSecurityEvent();
        event.setPublicId(UUID.randomUUID());
        event.setAttempt(attempt);
        event.setAttemptPublicId(attemptId);
        event.setSessionPublicId(sessionId);
        event.setStudentPublicId(studentId);
        event.setTenantId(tenantId);
        event.setClientEventId(clientEventId);
        event.setViolationType(type);
        event.setSeverity(LockdownViolationSeverity.WARNING);
        event.setDetail(detail);
        event.setClientOccurredAt(detectedAt.minusSeconds(1));
        event.setDetectedAt(detectedAt);
        return event;
    }

    private CurrentUser caller(UUID callerTenantId) {
        return new CurrentUser(studentId, callerTenantId, List.of("STUDENT"));
    }
}
