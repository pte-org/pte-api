package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import com.pte.proctoring.internal.exception.ProctorSessionNotFoundException;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Opens/closes a proctor's live monitoring window over an exam session. Open
 * is the ONE place proctoring calls out to session (per WS connection, not
 * per command). Open is idempotent: reconnecting to an already-ACTIVE session
 * resumes it rather than duplicating.
 */
@Service
public class ProctorSessionService {

    private final ProctorSessionRepository proctorSessionRepository;
    private final SessionService sessionService;
    private final ProctorMapper mapper;

    public ProctorSessionService(ProctorSessionRepository proctorSessionRepository, SessionService sessionService,
                                 ProctorMapper mapper) {
        this.proctorSessionRepository = proctorSessionRepository;
        this.sessionService = sessionService;
        this.mapper = mapper;
    }

    @Transactional
    public ProctorSessionResponse open(UUID sessionPublicId, CurrentUser caller) {
        UUID proctorPublicId = caller.userId();
        return proctorSessionRepository
                .findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                        sessionPublicId, proctorPublicId, caller.tenantId(), ProctorSessionStatus.ACTIVE)
                .map(mapper::toResponse)
                .orElseGet(() -> openNew(sessionPublicId, proctorPublicId));
    }

    @Transactional
    public void close(UUID proctorSessionPublicId, CurrentUser caller) {
        ProctorSession session = findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId());
        if (session.isActive()) {
            session.end();
            proctorSessionRepository.save(session);
        }
    }

    ProctorSession findOwned(UUID publicId, UUID proctorPublicId, UUID tenantId) {
        return proctorSessionRepository.findByPublicIdAndProctorPublicIdAndTenantId(publicId, proctorPublicId, tenantId)
                .orElseThrow(ProctorSessionNotFoundException::new);
    }

    private ProctorSessionResponse openNew(UUID sessionPublicId, UUID proctorPublicId) {
        // Propagates session's own ProctorNotAssignedException (403) unmodified
        // when the caller isn't assigned — same externally observable behavior
        // as the pre-split assignment-check-then-fetch path, without the network call.
        ProctorAssignmentCheckResponse assignment = sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId);
        ProctorSession session = new ProctorSession();
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(assignment.tenantId());
        ProctorSession saved = proctorSessionRepository.save(session);
        return mapper.toResponse(saved);
    }
}
