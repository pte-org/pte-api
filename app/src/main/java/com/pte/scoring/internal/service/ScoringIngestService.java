package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.internal.service.objective.ScoringMethods;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Opens an {@link AiEvaluation} for every answer attempt has submitted — an in-process pull run right before a
 * host scoring request (dependency order: attempt ──> scoring, so scoring calls out to attempt, never the reverse).
 * The row records only what scoring needs to route the answer (task type, section, scoring method read from the
 * exam item); the answer content itself stays in attempt. Idempotent by the (answer, source) unique key: an
 * answer that already has a row, in any status, is never inserted again. Opening evaluations only makes answers
 * reviewable; AI scoring still starts only from the host command. Objective answers are the exception: scoring
 * them is a free, synchronous rule check, so they are scored as they are opened and never wait on the host.
 */
@Service
public class ScoringIngestService {

    private static final Logger log = LoggerFactory.getLogger(ScoringIngestService.class);

    private final AttemptService attemptService;
    private final AiEvaluationRepository evaluationRepository;
    private final ObjectiveScoringService objectiveScoringService;
    private final Clock clock;

    public ScoringIngestService(AttemptService attemptService, AiEvaluationRepository evaluationRepository,
                                ObjectiveScoringService objectiveScoringService, Clock clock) {
        this.attemptService = attemptService;
        this.evaluationRepository = evaluationRepository;
        this.objectiveScoringService = objectiveScoringService;
        this.clock = clock;
    }

    /** Inserts evaluations for answers not seen yet; existing rows are left as they are. */
    @Transactional
    public void ingestForSession(UUID sessionPublicId, UUID tenantId) {
        ingest(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId));
    }

    /**
     * Ingests one submitted attempt right after it commits, so the host can review its answers before scoring.
     * Always its own transaction: it runs after the submitting transaction has completed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ingestSubmittedAttempt(UUID attemptPublicId, UUID tenantId) {
        ingest(attemptService.getSubmittedAnswersForAttempt(attemptPublicId, tenantId));
    }

    private void ingest(List<SubmittedAnswerView> views) {
        for (SubmittedAnswerView view : views) {
            if (evaluationRepository.findByAnswerPublicId(view.answerPublicId()).isPresent()) {
                continue;
            }
            AiEvaluation evaluation = new AiEvaluation(view.tenantId(), view.sessionPublicId(),
                    view.attemptPublicId(), view.answerPublicId(), view.itemPublicId(),
                    view.examDefinitionPublicId(), view.taskType(), view.section(),
                    ScoringMethods.of(view.runtime()).orElse(null));
            scoreIfObjective(evaluation, view);
            try {
                evaluationRepository.save(evaluation);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent ingest of the same answer — the unique key already dedups it.
            }
        }
    }

    /** A rule that cannot score this answer leaves it PENDING for the host's scoring request to retry. */
    private void scoreIfObjective(AiEvaluation evaluation, SubmittedAnswerView view) {
        if (!objectiveScoringService.supports(evaluation.getScoringMethod())) {
            return;
        }
        try {
            evaluation.markScored(objectiveScoringService.score(view), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Could not score objective answer {} on ingest; it stays pending", view.answerPublicId(), ex);
        }
    }
}
