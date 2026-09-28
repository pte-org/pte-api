package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptSecurityEvent;
import com.pte.attempt.dto.response.AttemptSecurityEventView;
import com.pte.attempt.internal.repository.AttemptSecurityEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Internal query adapter behind the public AttemptService boundary. */
@Service
public class AttemptSecurityAuditQueryService {

    private final AttemptSecurityEventRepository securityEventRepository;

    public AttemptSecurityAuditQueryService(AttemptSecurityEventRepository securityEventRepository) {
        this.securityEventRepository = securityEventRepository;
    }

    @Transactional(readOnly = true)
    public List<AttemptSecurityEventView> findForSession(UUID sessionPublicId, UUID tenantId,
                                                         Instant cursorAt, UUID cursorId, int limit) {
        return securityEventRepository.findForSession(sessionPublicId, tenantId, cursorAt, cursorId,
                        PageRequest.of(0, limit))
                .stream()
                .map(this::toView)
                .toList();
    }

    private AttemptSecurityEventView toView(AttemptSecurityEvent event) {
        return new AttemptSecurityEventView(event.getPublicId(), event.getAttemptPublicId(),
                event.getStudentPublicId(), event.getSessionPublicId(), event.getViolationType().name(),
                event.getSeverity().name(), event.getClientEventId(), event.getDetail(),
                event.getClientOccurredAt(), event.getDetectedAt());
    }
}
