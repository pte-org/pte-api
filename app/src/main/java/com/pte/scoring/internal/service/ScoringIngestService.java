package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.objective.ScoringMethods;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Opens an {@link AiEvaluation} for every answer attempt has submitted — an in-process pull run right before a
 * host scoring request (dependency order: attempt ──> scoring, so scoring calls out to attempt, never the reverse).
 * The row records only what scoring needs to route the answer (task type, section, scoring method read from the
 * exam item); the answer content itself stays in attempt. Idempotent by the (answer, source) unique key: an
 * answer that already has a row, in any status, is never inserted again. Opening evaluations only makes answers
 * reviewable; scoring still starts only from the host command.
 */
@Service
public class ScoringIngestService {

    private final AttemptService attemptService;
    private final AiEvaluationRepository evaluationRepository;

    public ScoringIngestService(AttemptService attemptService, AiEvaluationRepository evaluationRepository) {
        this.attemptService = attemptService;
        this.evaluationRepository = evaluationRepository;
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
            try {
                evaluationRepository.save(evaluation);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent ingest of the same answer — the unique key already dedups it.
            }
        }
    }
}
