package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptSecurityEvent;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.enums.LockdownViolationSeverity;
import com.pte.attempt.internal.dto.request.RecordSecurityViolationRequest;
import com.pte.attempt.internal.dto.response.SecurityViolationReceipt;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.SecurityAuditDisabledException;
import com.pte.attempt.internal.exception.SecurityAuditEventConflictException;
import com.pte.attempt.internal.exception.SecurityAuditPolicyUnresolvedException;
import com.pte.attempt.internal.repository.AttemptSecurityEventRepository;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Records authenticated student lockdown evidence against the attempt. */
@Service
public class AttemptSecurityAuditService {

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptSecurityEventRepository securityEventRepository;

    public AttemptSecurityAuditService(ExamAttemptRepository examAttemptRepository,
                                       AttemptSecurityEventRepository securityEventRepository) {
        this.examAttemptRepository = examAttemptRepository;
        this.securityEventRepository = securityEventRepository;
    }

    @Transactional
    public SecurityViolationReceipt record(UUID attemptPublicId, RecordSecurityViolationRequest request,
                                           CurrentUser caller) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null) {
            throw new AttemptNotFoundException();
        }

        ExamAttempt attempt = examAttemptRepository
                .findWithPinnedByPublicIdAndStudentPublicId(attemptPublicId, caller.userId())
                .filter(candidate -> Objects.equals(candidate.getTenantId(), caller.tenantId()))
                .orElseThrow(AttemptNotFoundException::new);

        // The attempt row lock serializes two concurrent first deliveries for
        // the same clientEventId. The database unique constraint remains the
        // final safety net if another writer bypasses this service.
        ExamAttempt lockedAttempt = examAttemptRepository.findWithLockById(attempt.getId())
                .orElseThrow(AttemptNotFoundException::new);
        PinnedExamSnapshot pinned = lockedAttempt.getPinnedSnapshot();
        LockdownViolationSeverity severity = resolveSeverity(pinned);

        String clientEventId = request.clientEventId().trim();
        Optional<AttemptSecurityEvent> existing = securityEventRepository
                .findByAttemptIdAndClientEventId(lockedAttempt.getId(), clientEventId);
        if (existing.isPresent()) {
            AttemptSecurityEvent event = existing.get();
            if (!samePayload(event, request)) {
                throw new SecurityAuditEventConflictException();
            }
            return toReceipt(event, true);
        }

        Instant detectedAt = Instant.now();
        AttemptSecurityEvent event = new AttemptSecurityEvent();
        event.setAttempt(lockedAttempt);
        event.setAttemptPublicId(lockedAttempt.getPublicId());
        event.setSessionPublicId(lockedAttempt.getSessionPublicId());
        event.setStudentPublicId(lockedAttempt.getStudentPublicId());
        event.setTenantId(lockedAttempt.getTenantId());
        event.setClientEventId(clientEventId);
        event.setViolationType(request.violationType());
        event.setSeverity(severity);
        event.setDetail(request.detail());
        event.setClientOccurredAt(request.clientOccurredAt());
        event.setDetectedAt(detectedAt);

        return toReceipt(securityEventRepository.save(event), false);
    }

    private LockdownViolationSeverity resolveSeverity(PinnedExamSnapshot pinned) {
        if (pinned == null || pinned.getLockdownMode() == null || pinned.getLockdownMode().isBlank()) {
            throw new SecurityAuditPolicyUnresolvedException();
        }
        return switch (pinned.getLockdownMode().trim().toUpperCase(Locale.ROOT)) {
            case "STANDARD" -> LockdownViolationSeverity.WARNING;
            case "STRICT" -> LockdownViolationSeverity.CRITICAL;
            case "NONE" -> throw new SecurityAuditDisabledException();
            default -> throw new SecurityAuditPolicyUnresolvedException();
        };
    }

    private boolean samePayload(AttemptSecurityEvent event, RecordSecurityViolationRequest request) {
        return event.getViolationType() == request.violationType()
                && Objects.equals(event.getDetail(), request.detail())
                && Objects.equals(event.getClientOccurredAt(), request.clientOccurredAt());
    }

    private SecurityViolationReceipt toReceipt(AttemptSecurityEvent event, boolean duplicate) {
        return new SecurityViolationReceipt(event.getPublicId(), event.getAttemptPublicId(),
                event.getClientEventId(), event.getViolationType(), event.getSeverity(), event.getDetectedAt(),
                duplicate);
    }
}
