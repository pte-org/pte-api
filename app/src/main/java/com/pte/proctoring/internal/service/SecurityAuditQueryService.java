package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.response.SecurityAuditEntryResponse;
import com.pte.proctoring.internal.dto.response.SecurityAuditPageResponse;
import com.pte.proctoring.internal.dto.response.SecurityAuditSource;
import com.pte.proctoring.internal.exception.InvalidSecurityAuditCursorException;
import com.pte.proctoring.internal.exception.InvalidSecurityAuditLimitException;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The security audit of one exam session: lockdown evidence from the student clients and violations flagged by
 * proctors, in one chronological stream with an opaque cursor. Both kinds are {@code ExamIncident} rows, so one
 * keyset query orders them.
 */
@Service
public class SecurityAuditQueryService {

    private static final Set<IncidentOrigin> EVIDENCE_ORIGINS =
            EnumSet.of(IncidentOrigin.AUTO, IncidentOrigin.PROCTOR_FLAG);

    private final ProctorAccessPolicy proctorAccessPolicy;
    private final ExamIncidentRepository incidentRepository;

    public SecurityAuditQueryService(ProctorAccessPolicy proctorAccessPolicy,
                                     ExamIncidentRepository incidentRepository) {
        this.proctorAccessPolicy = proctorAccessPolicy;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public SecurityAuditPageResponse listForSession(UUID sessionPublicId, int requestedLimit, String rawCursor,
                                                    CurrentUser caller) {
        int limit = normalizeLimit(requestedLimit);
        SecurityAuditCursor cursor = decodeCursor(rawCursor);
        UUID tenantId = proctorAccessPolicy.authorizeReadAssigned(sessionPublicId, caller);
        PageRequest window = PageRequest.of(0, limit + 1);
        List<ExamIncident> fetched = cursor.detectedAt() == null
                ? incidentRepository.findAuditFirstPage(sessionPublicId, tenantId, EVIDENCE_ORIGINS, window)
                : incidentRepository.findAuditAfter(sessionPublicId, tenantId, EVIDENCE_ORIGINS,
                        cursor.detectedAt(), cursor.publicId(), window);
        boolean hasNext = fetched.size() > limit;
        List<ExamIncident> page = hasNext ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasNext && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1)) : null;
        return new SecurityAuditPageResponse(page.stream().map(this::toEntry).toList(), nextCursor);
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > ProctorConstants.SECURITY_AUDIT_MAX_LIMIT) {
            throw new InvalidSecurityAuditLimitException();
        }
        return requestedLimit;
    }

    private SecurityAuditCursor decodeCursor(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return new SecurityAuditCursor(null, null);
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(ProctorConstants.CURSOR_PARTS_MISSING);
            }
            return new SecurityAuditCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException invalidCursor) {
            throw new InvalidSecurityAuditCursorException();
        }
    }

    private String encodeCursor(ExamIncident incident) {
        String value = incident.getDetectedAt() + "|" + incident.getPublicId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private SecurityAuditEntryResponse toEntry(ExamIncident incident) {
        boolean studentEvidence = incident.getOrigin() == IncidentOrigin.AUTO;
        return new SecurityAuditEntryResponse(incident.getPublicId(),
                studentEvidence ? SecurityAuditSource.STUDENT_LOCKDOWN : SecurityAuditSource.PROCTOR,
                incident.getAttemptPublicId(), studentEvidence ? incident.getStudentPublicId() : null,
                incident.getType(), incident.getSeverity(), incident.getDetectedAt(), incident.getClientEventId(),
                incident.getDescription());
    }

    private record SecurityAuditCursor(Instant detectedAt, UUID publicId) {
    }
}
