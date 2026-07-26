package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.client.SchedulingClient;
import com.pte.proctor.client.dto.SchedulingProctorAssignmentResponse;
import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.enums.ProctorSessionStatus;
import com.pte.proctor.domain.exception.ProctorAssignmentCheckFailedException;
import com.pte.proctor.domain.exception.ProctorSessionNotFoundException;
import com.pte.proctor.dto.response.ProctorSessionResponse;
import com.pte.proctor.mapper.ProctorMapper;
import com.pte.proctor.repository.ProctorSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Opens/closes a proctor's live monitoring window over an exam session. Open
 * is the ONE place proctor calls out to scheduling (per WS connection, not
 * per command) — see {@code phase-10 Design Constraints}. Open is idempotent:
 * reconnecting to an already-ACTIVE session resumes it rather than duplicating.
 */
@Service
public class ProctorSessionService {

    private final ProctorSessionRepository proctorSessionRepository;
    private final SchedulingClient schedulingClient;
    private final ProctorMapper mapper;

    public ProctorSessionService(ProctorSessionRepository proctorSessionRepository, SchedulingClient schedulingClient,
                                 ProctorMapper mapper) {
        this.proctorSessionRepository = proctorSessionRepository;
        this.schedulingClient = schedulingClient;
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
        SchedulingProctorAssignmentResponse assignment = schedulingClient.checkAssignment(sessionPublicId, proctorPublicId);
        if (assignment == null) {
            throw new ProctorAssignmentCheckFailedException();
        }
        ProctorSession session = new ProctorSession();
        session.setSessionPublicId(sessionPublicId);
        session.setProctorPublicId(proctorPublicId);
        session.setTenantId(assignment.tenantId());
        ProctorSession saved = proctorSessionRepository.save(session);
        return mapper.toResponse(saved);
    }
}
