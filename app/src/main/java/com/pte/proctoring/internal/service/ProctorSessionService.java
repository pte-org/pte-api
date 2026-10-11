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
        UUID tenantId = caller.tenantId();
        // Lock session and check OPEN + window first to eliminate race with Host revocation
        sessionService.lockOpenWithinWindowForProctorOperation(sessionPublicId, tenantId);
        // Reauthorize assignment under the session lock
        ProctorAssignmentCheckResponse assignment = sessionService.checkProctorAssignment(sessionPublicId, proctorPublicId);

        return proctorSessionRepository
                .findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                        sessionPublicId, proctorPublicId, tenantId, ProctorSessionStatus.ACTIVE)
                .map(mapper::toResponse)
                .orElseGet(() -> openNewWithAssignment(sessionPublicId, proctorPublicId, assignment.tenantId()));
    }

    @Transactional
    public void close(UUID proctorSessionPublicId, CurrentUser caller) {
        ProctorSession session = findOwnedWithLock(proctorSessionPublicId, caller.userId(), caller.tenantId());
        if (session.isActive()) {
            session.end();
            proctorSessionRepository.save(session);
        }
    }

    ProctorSession findOwned(UUID publicId, UUID proctorPublicId, UUID tenantId) {
        return proctorSessionRepository.findByPublicIdAndProctorPublicIdAndTenantId(publicId, proctorPublicId, tenantId)
                .orElseThrow(ProctorSessionNotFoundException::new);
    }

    ProctorSession findOwnedWithLock(UUID publicId, UUID proctorPublicId, UUID tenantId) {
        return proctorSessionRepository.findWithLockByPublicIdAndProctorPublicIdAndTenantId(publicId, proctorPublicId, tenantId)
                .orElseThrow(ProctorSessionNotFoundException::new);
    }

    private ProctorSessionResponse openNewWithAssignment(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        ProctorSession session = new ProctorSession();
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(tenantId);
        ProctorSession saved = proctorSessionRepository.save(session);
        return mapper.toResponse(saved);
    }
}
