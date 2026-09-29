package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSecurityEventView;
import com.pte.proctoring.domain.ViolationEvent;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.response.SecurityAuditEntryResponse;
import com.pte.proctoring.internal.dto.response.SecurityAuditPageResponse;
import com.pte.proctoring.internal.dto.response.SecurityAuditSource;
import com.pte.proctoring.internal.exception.InvalidSecurityAuditCursorException;
import com.pte.proctoring.internal.exception.InvalidSecurityAuditLimitException;
import com.pte.proctoring.internal.repository.ViolationEventRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Authorization-aware adapter that combines the old proctor chain with the
 * new attempt-owned student lockdown evidence.
 */
@Service
public class SecurityAuditQueryService {

    private static final String LEGACY_PROCTOR_SEVERITY = "WARNING";
    private static final Comparator<SecurityAuditEntryResponse> ORDER = Comparator
            .comparing(SecurityAuditEntryResponse::detectedAt)
            .thenComparing(SecurityAuditEntryResponse::publicId);

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ViolationEventRepository violationEventRepository;

    public SecurityAuditQueryService(SessionService sessionService, AttemptService attemptService,
                                     ViolationEventRepository violationEventRepository) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.violationEventRepository = violationEventRepository;
    }

    @Transactional(readOnly = true)
    public SecurityAuditPageResponse listForSession(UUID sessionPublicId, int requestedLimit, String rawCursor,
                                                    CurrentUser caller) {
        int limit = normalizeLimit(requestedLimit);
        SecurityAuditCursor cursor = decodeCursor(rawCursor);
        UUID tenantId = authorize(sessionPublicId, caller);
        int fetchSize = limit + 1;

        List<SecurityAuditEntryResponse> entries = new ArrayList<>();
        violationEventRepository.findForSecurityAudit(sessionPublicId, tenantId, cursor.detectedAt(),
                        cursor.publicId(), PageRequest.of(0, fetchSize))
                .stream()
                .map(this::toProctorEntry)
                .forEach(entries::add);
        attemptService.getSecurityEventsForSession(sessionPublicId, tenantId, cursor.detectedAt(),
                        cursor.publicId(), fetchSize)
                .stream()
                .map(this::toStudentEntry)
                .forEach(entries::add);

        entries.sort(ORDER);
        boolean hasNext = entries.size() > limit;
        List<SecurityAuditEntryResponse> page = hasNext
                ? new ArrayList<>(entries.subList(0, limit))
                : entries;
        String nextCursor = hasNext && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1)) : null;
        return new SecurityAuditPageResponse(page, nextCursor);
    }

    private UUID authorize(UUID sessionPublicId, CurrentUser caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        if (caller.hasRole("HOST_ADMIN")) {
            if (caller.tenantId() == null) {
                throw new AccessDeniedException("Tenant context is required");
            }
            sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
            return caller.tenantId();
        }
        if (caller.hasRole("PROCTOR")) {
            ProctorAssignmentCheckResponse assignment = sessionService.checkProctorAssignment(
                    sessionPublicId, caller.userId());
            return assignment.tenantId();
        }
        throw new AccessDeniedException("Host or proctor role is required");
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1 || requestedLimit > ProctorConstants.SECURITY_AUDIT_MAX_LIMIT) {
            throw new InvalidSecurityAuditLimitException();
        }
        return requestedLimit;
    }

    private SecurityAuditCursor decodeCursor(String rawCursor) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return SecurityAuditCursor.empty();
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("missing cursor parts");
            }
            return new SecurityAuditCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException invalidCursor) {
            throw new InvalidSecurityAuditCursorException();
        }
    }

    private String encodeCursor(SecurityAuditEntryResponse entry) {
        String value = entry.detectedAt() + "|" + entry.publicId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private SecurityAuditEntryResponse toStudentEntry(AttemptSecurityEventView event) {
        return new SecurityAuditEntryResponse(event.publicId(), SecurityAuditSource.STUDENT_LOCKDOWN,
                event.attemptPublicId(), event.studentPublicId(), event.violationType(),
                event.severity(), event.detectedAt(), event.clientEventId(), event.detail());
    }

    private SecurityAuditEntryResponse toProctorEntry(ViolationEvent event) {
        return new SecurityAuditEntryResponse(event.getPublicId(), SecurityAuditSource.PROCTOR,
                event.getAttemptPublicId(), null, event.getViolationType().name(), LEGACY_PROCTOR_SEVERITY,
                event.getDetectedAt(), null, event.getDetail());
    }

    private record SecurityAuditCursor(Instant detectedAt, UUID publicId) {
        private static SecurityAuditCursor empty() {
            return new SecurityAuditCursor(null, null);
        }
    }
}
