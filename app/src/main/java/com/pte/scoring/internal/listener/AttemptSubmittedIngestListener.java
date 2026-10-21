package com.pte.scoring.internal.listener;

import com.pte.attempt.dto.event.AttemptSubmittedEvent;
import com.pte.scoring.internal.service.ScoringIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Copies a submitted attempt's answers into scoring as soon as the submit commits, so the host
 * can review them before choosing to score. Best effort: a failure here must not touch the
 * student's submission, and the host's scoring request ingests whatever is still missing.
 */
@Component
public class AttemptSubmittedIngestListener {

    private static final Logger log = LoggerFactory.getLogger(AttemptSubmittedIngestListener.class);

    private final ScoringIngestService ingestService;

    public AttemptSubmittedIngestListener(ScoringIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @TransactionalEventListener
    public void onAttemptSubmitted(AttemptSubmittedEvent event) {
        try {
            ingestService.ingestSubmittedAttempt(event.attemptPublicId(), event.tenantId());
        } catch (RuntimeException ex) {
            log.warn("Could not ingest answers of submitted attempt {}; the host's scoring request will",
                    event.attemptPublicId(), ex);
        }
    }
}
