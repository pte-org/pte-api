package com.pte.practice.internal.messaging;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.service.PracticeScoringRequestFactory;
import com.pte.practice.internal.service.PracticeScoringStateService;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.ScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Scores queued practice answers through the exam's scorers and writes the result back for the job's dispatch token.
 * A provider failure becomes a FAILED item the student can retry; any other error is rethrown, which the container
 * rejects to the dead-letter queue (no redelivery, so the vendor is never billed twice for one job).
 */
@Component
public class PracticeScoringWorker {

    private static final Logger log = LoggerFactory.getLogger(PracticeScoringWorker.class);

    private final PracticeScoringStateService stateService;
    private final PracticeScoringRequestFactory requestFactory;
    private final ScoringService scoringService;
    private final Clock clock;

    public PracticeScoringWorker(PracticeScoringStateService stateService,
            PracticeScoringRequestFactory requestFactory, ScoringService scoringService, Clock clock) {
        this.stateService = stateService;
        this.requestFactory = requestFactory;
        this.scoringService = scoringService;
        this.clock = clock;
    }

    @RabbitListener(queues = PracticeConstants.PRACTICE_SCORING_QUEUE,
            containerFactory = "practiceScoringRabbitListenerContainerFactory")
    public void handle(PracticeScoringJob job) {
        PracticeSessionItem item = stateService.claim(job.itemPublicId(), job.dispatchToken(), clock.instant());
        if (item == null) {
            log.info("Practice scoring job for item {} is stale, already claimed or final; skipped",
                    job.itemPublicId());
            return;
        }
        UUID claimToken = item.getScoringDispatchToken();
        try {
            PracticeAiEvaluation evaluation = scoringService
                    .scorePracticeAnswer(requestFactory.build(item, job.tenantId()));
            stateService.complete(job.itemPublicId(), claimToken, evaluation);
        } catch (PracticeScoringException ex) {
            log.warn("Practice scoring for item {} failed: {}", job.itemPublicId(), ex.failure());
            stateService.fail(job.itemPublicId(), claimToken, ex.failure());
        } catch (OptimisticLockingFailureException ex) {
            // The item changed under us (retry, re-dispatch or timeout); that write owns the outcome.
            log.info("Practice scoring result for item {} dropped after a concurrent write", job.itemPublicId());
        }
    }

    @RabbitListener(queues = PracticeConstants.PRACTICE_SCORING_DLQ,
            containerFactory = "practiceScoringRabbitListenerContainerFactory")
    public void handleDeadLetter(PracticeScoringJob job) {
        boolean marked = stateService.fail(job.itemPublicId(), job.dispatchToken(), PracticeScoringFailure.INTERNAL);
        log.warn("Practice scoring job for item {} was dead-lettered; marked failed: {}", job.itemPublicId(), marked);
    }
}
