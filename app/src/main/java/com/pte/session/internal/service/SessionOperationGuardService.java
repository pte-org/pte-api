package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.exception.HostContextRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.session.internal.exception.SessionNotClosedForGradingCohortException;
import com.pte.session.internal.exception.SessionNotClosedForReportPublicationException;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Transactional session guards used by attempt, scoring, and reporting commands. */
@Service
public class SessionOperationGuardService {

    private final ExamSessionRepository sessionRepository;

    public SessionOperationGuardService(ExamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void lockClosedForGradingCohort(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionNotClosedForGradingCohortException();
        }
    }

    @Transactional
    public void lockForExaminerAssignment(UUID publicId, UUID tenantId) {
        if (tenantId == null) {
            throw new HostContextRequiredException();
        }
        sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    @Transactional
    public void lockForScoreReviewMutation(UUID publicId, UUID tenantId) {
        if (tenantId == null) {
            throw new HostContextRequiredException();
        }
        sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
    }

    @Transactional
    public void lockOpenForAttemptOperation(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(NotEntitledException::new);
        SessionEntryGate.requireOpen(session);
    }

    @Transactional
    public void lockClosedForReportPublication(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionNotClosedForReportPublicationException();
        }
    }
}
