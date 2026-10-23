package com.pte.practice.internal.service;

import com.pte.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Runs provider speech scoring only after the answer transaction commits.
 * The answer remains durable when Azure is slow or temporarily unavailable.
 */
@Service
public class PracticeSpeechScoringDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeSpeechScoringDispatcher.class);

    private final PracticeSpeechScoringPersistenceService persistenceService;
    private final ScoringService scoringService;

    public PracticeSpeechScoringDispatcher(PracticeSpeechScoringPersistenceService persistenceService,
            ScoringService scoringService) {
        this.persistenceService = persistenceService;
        this.scoringService = scoringService;
    }

    public void dispatchAfterCommit(UUID itemPublicId, UUID tenantId) {
        Runnable scoring = () -> scoreAndPersist(itemPublicId, tenantId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            scoring.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scoring.run();
            }
        });
    }

    private void scoreAndPersist(UUID itemPublicId, UUID tenantId) {
        try {
            persistenceService.load(itemPublicId).ifPresent(input -> scoringService.evaluatePracticeSpeech(
                    input.taskCode(), input.mediaPublicId(), input.promptText(), input.referenceAnswerText(), tenantId)
                    .ifPresent(result -> persistenceService.persist(result, input.itemPublicId())));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage();
            if (reason == null || !reason.matches("[A-Za-z0-9 _.,:;()\\-]{1,160}")) {
                reason = "UNCLASSIFIED";
            }
            LOG.warn("Practice speech scoring dispatch failed: errorType={}, reason={}",
                    exception.getClass().getSimpleName(), reason);
        }
    }

}
