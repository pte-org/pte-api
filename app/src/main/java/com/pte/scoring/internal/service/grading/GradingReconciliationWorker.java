package com.pte.scoring.internal.service.grading;

import com.pte.session.SessionService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Executes grading reconciliation in a separate Spring-managed transaction per session,
 * acquiring the session mutex before any cohort/member locks.
 */
@Service
public class GradingReconciliationWorker {

    private final SessionService sessionService;
    private final GradingCohortService cohortService;

    public GradingReconciliationWorker(SessionService sessionService, @Lazy GradingCohortService cohortService) {
        this.sessionService = sessionService;
        this.cohortService = cohortService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSession(UUID sessionPublicId, UUID tenantId) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        cohortService.reconcileIfPresent(sessionPublicId, tenantId);
    }
}
