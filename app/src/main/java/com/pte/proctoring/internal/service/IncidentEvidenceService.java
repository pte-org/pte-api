package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptEvidenceContextView;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.domain.enums.LockdownViolationSeverity;
import com.pte.proctoring.domain.enums.LockdownViolationType;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.request.RecordSecurityViolationRequest;
import com.pte.proctoring.internal.dto.response.SecurityViolationReceipt;
import com.pte.proctoring.internal.exception.SecurityAuditDisabledException;
import com.pte.proctoring.internal.exception.SecurityAuditEventConflictException;
import com.pte.proctoring.internal.exception.SecurityAuditPolicyUnresolvedException;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Records authenticated student lockdown evidence against the attempt as an AUTO incident. */
@Service
public class IncidentEvidenceService {

    private final AttemptService attemptService;
    private final ExamIncidentRepository incidentRepository;

    public IncidentEvidenceService(AttemptService attemptService, ExamIncidentRepository incidentRepository) {
        this.attemptService = attemptService;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public SecurityViolationReceipt record(UUID attemptPublicId, RecordSecurityViolationRequest request,
                                           CurrentUser caller) {
        // The attempt row lock serializes two concurrent first deliveries of the same clientEventId. The unique
        // constraint on (attempt, client event) stays the final safety net if another writer bypasses this service.
        AttemptEvidenceContextView attempt = attemptService.lockOwnAttemptForEvidence(attemptPublicId, caller);
        LockdownViolationSeverity severity = resolveSeverity(attempt.lockdownMode());

        String clientEventId = request.clientEventId().trim();
        Optional<ExamIncident> existing = incidentRepository.findByAttemptPublicIdAndClientEventId(
                attempt.attemptPublicId(), clientEventId);
        if (existing.isPresent()) {
            if (!samePayload(existing.get(), request)) {
                throw new SecurityAuditEventConflictException();
            }
            return toReceipt(existing.get(), true);
        }

        ExamIncident incident = new ExamIncident();
        incident.setTenantId(attempt.tenantId());
        incident.setSessionPublicId(attempt.sessionPublicId());
        incident.setStudentPublicId(attempt.studentPublicId());
        incident.setAttemptPublicId(attempt.attemptPublicId());
        incident.setOrigin(IncidentOrigin.AUTO);
        incident.setType(request.violationType().name());
        incident.setTitle(request.violationType().name());
        incident.setDescription(request.detail());
        incident.setSeverity(severity.name());
        incident.setClientEventId(clientEventId);
        incident.setClientOccurredAt(request.clientOccurredAt());
        incident.setDetectedAt(Instant.now());
        return toReceipt(incidentRepository.save(incident), false);
    }

    private LockdownViolationSeverity resolveSeverity(String lockdownMode) {
        if (lockdownMode == null || lockdownMode.isBlank()) {
            throw new SecurityAuditPolicyUnresolvedException();
        }
        return switch (lockdownMode.trim().toUpperCase(Locale.ROOT)) {
            case ProctorConstants.LOCKDOWN_MODE_STANDARD -> LockdownViolationSeverity.WARNING;
            case ProctorConstants.LOCKDOWN_MODE_STRICT -> LockdownViolationSeverity.CRITICAL;
            case ProctorConstants.LOCKDOWN_MODE_NONE -> throw new SecurityAuditDisabledException();
            default -> throw new SecurityAuditPolicyUnresolvedException();
        };
    }

    private boolean samePayload(ExamIncident incident, RecordSecurityViolationRequest request) {
        return Objects.equals(incident.getType(), request.violationType().name())
                && Objects.equals(incident.getDescription(), request.detail())
                && Objects.equals(incident.getClientOccurredAt(), request.clientOccurredAt());
    }

    private SecurityViolationReceipt toReceipt(ExamIncident incident, boolean duplicate) {
        return new SecurityViolationReceipt(incident.getPublicId(), incident.getAttemptPublicId(),
                incident.getClientEventId(),
                LockdownViolationType.valueOf(incident.getType()),
                LockdownViolationSeverity.valueOf(incident.getSeverity()), incident.getDetectedAt(), duplicate);
    }
}
