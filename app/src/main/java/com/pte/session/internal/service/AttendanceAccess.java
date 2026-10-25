package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.internal.exception.ProctorNotAssignedException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Session lookup and the "host, or a proctor assigned to this session" rule shared by every attendance operation. */
@Component
class AttendanceAccess {

    private final ExamSessionRepository sessionRepository;
    private final ExamSessionProctorRepository proctorRepository;

    AttendanceAccess(ExamSessionRepository sessionRepository, ExamSessionProctorRepository proctorRepository) {
        this.sessionRepository = sessionRepository;
        this.proctorRepository = proctorRepository;
    }

    /** Locks the session row so concurrent attendance writes of one session serialize. */
    ExamSession lockSession(UUID sessionPublicId, UUID tenantId) {
        return sessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    ExamSession readSession(UUID sessionPublicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    void requireHostOrAssignedProctor(UUID sessionPublicId, CurrentUser caller, boolean isHost) {
        if (!isHost && !proctorRepository.isProctorAssigned(sessionPublicId, caller.userId(), caller.tenantId())) {
            throw new ProctorNotAssignedException();
        }
    }
}
