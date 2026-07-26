package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.event.AttemptScoredEvent;
import com.pte.scoring.messaging.outbox.OutboxWriter;
import com.pte.scoring.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Shared "is this attempt fully scored?" check (phase-09) — called from every
 * place an answer can reach a terminal state: {@code ScoringCommandConsumer}
 * (objective + AI dispatch), {@code AiScoringWorker} (Read Aloud, no review
 * needed), {@code ScoringReviewService} (Write Essay, after host approval).
 * "Fully scored" = zero rows left in a non-terminal status — honest completion
 * (Phase 7): an unsupported task type stuck in PENDING still blocks this
 * forever, same as before.
 */
@Service
public class AttemptCompletionService {

    private static final List<ScoringAnswerStatus> NON_TERMINAL = List.of(
            ScoringAnswerStatus.PENDING, ScoringAnswerStatus.AI_SCORING, ScoringAnswerStatus.AI_SCORED_PENDING_REVIEW);

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final OutboxWriter outboxWriter;

    public AttemptCompletionService(ScoringAnswerRepository scoringAnswerRepository, OutboxWriter outboxWriter) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.outboxWriter = outboxWriter;
    }

    public void checkAndEmitIfComplete(UUID attemptPublicId, UUID sessionPublicId, UUID tenantId) {
        boolean stillIncomplete = scoringAnswerRepository.existsByAttemptPublicIdAndStatusIn(attemptPublicId, NON_TERMINAL);
        if (stillIncomplete) {
            return;
        }
        outboxWriter.write(ScoringConstants.AGGREGATE_ATTEMPT, attemptPublicId.toString(),
                ScoringConstants.EVENT_ATTEMPT_SCORED,
                new AttemptScoredEvent(attemptPublicId, sessionPublicId, tenantId),
                tenantId);
    }
}
