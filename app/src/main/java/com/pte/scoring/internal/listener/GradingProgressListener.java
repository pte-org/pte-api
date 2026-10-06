package com.pte.scoring.internal.listener;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.internal.service.GradingCohortService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs completion reconciliation in the same transaction as the scoring write. */
@Component
public class GradingProgressListener {

    private final GradingCohortService cohortService;

    public GradingProgressListener(GradingCohortService cohortService) {
        this.cohortService = cohortService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onProgress(ScoringProgressChangedEvent event) {
        if (event != null && event.tenantPublicId() != null && event.sessionPublicId() != null) {
            cohortService.reconcileIfPresent(event.sessionPublicId(), event.tenantPublicId());
        }
    }
}
