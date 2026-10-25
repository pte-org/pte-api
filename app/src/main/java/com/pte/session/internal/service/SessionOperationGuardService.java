package com.pte.session.internal.service;

import com.pte.session.internal.service.enrollment.SessionEntryGate;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.exception.staff.HostContextRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.enrollment.NotEntitledException;
import com.pte.session.internal.exception.lifecycle.SessionNotClosedForExaminerAssignmentException;
import com.pte.session.internal.exception.lifecycle.SessionNotClosedForGradingCohortException;
import com.pte.session.internal.exception.lifecycle.SessionNotClosedForReportPublicationException;
import com.pte.session.internal.repository.ExamSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pte.tenancy.TenancyService;
import java.time.Clock;
import java.util.UUID;

/** Transactional session guards used by attempt, scoring, and reporting commands. */
@Service
public class SessionOperationGuardService {

    private final ExamSessionRepository sessionRepository;
    private final Clock clock;
    private final TenancyService tenancyService;

    public SessionOperationGuardService(ExamSessionRepository sessionRepository) {
        this(sessionRepository, Clock.systemUTC(), null);
    }

    @Autowired
    public SessionOperationGuardService(ExamSessionRepository sessionRepository, Clock clock,
            TenancyService tenancyService) {
        this.sessionRepository = sessionRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.tenancyService = tenancyService;
    }

    public SessionOperationGuardService(ExamSessionRepository sessionRepository, Clock clock) {
        this(sessionRepository, clock, null);
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
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionNotClosedForExaminerAssignmentException();
        }
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

    /** New attempts require an active tenant; existing attempt resume does not. */
    @Transactional(readOnly = true)
    public void assertTenantActiveForNewAttempt(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }

    @Transactional
    public void lockOpenWithinWindowForProctorOperation(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(NotEntitledException::new);
        SessionEntryGate.requireOpen(session);
        SessionEntryGate.requireWithinWindow(session, clock.instant());
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
