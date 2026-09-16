package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Pulls attempt's canonical submitted answers into scoring's own work-state
 * copy — replaces the old {@code AnswerSubmitted}-event live ingestion with
 * an in-process pull, run right before a host's scoring request is processed
 * (dependency order: attempt ──> scoring, so scoring calls out to attempt,
 * never the reverse). Idempotent by the {@code answer_public_id} unique
 * constraint: an already-ingested answer (any status, including already
 * SCORED) is left untouched, never re-inserted or overwritten.
 */
@Service
public class ScoringIngestService {

    private final AttemptService attemptService;
    private final ScoringAnswerRepository scoringAnswerRepository;

    public ScoringIngestService(AttemptService attemptService, ScoringAnswerRepository scoringAnswerRepository) {
        this.attemptService = attemptService;
        this.scoringAnswerRepository = scoringAnswerRepository;
    }

    @Transactional
    public void ingestForSession(UUID sessionPublicId, UUID tenantId) {
        for (SubmittedAnswerView view : attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)) {
            if (scoringAnswerRepository.findByAnswerPublicId(view.answerPublicId()).isPresent()) {
                continue;
            }
            ScoringAnswer answer = new ScoringAnswer();
            answer.setAnswerPublicId(view.answerPublicId());
            answer.setAttemptPublicId(view.attemptPublicId());
            answer.setPinnedItemPublicId(view.pinnedItemPublicId());
            answer.setSessionPublicId(view.sessionPublicId());
            answer.setTenantId(view.tenantId());
            answer.setTaskType(view.taskType());
            answer.setPayload(view.payload());
            answer.setCorrectAnswerText(view.correctAnswerText());
            answer.setOptionsJson(view.optionsJson());
            answer.setExpired(view.expired());
            try {
                scoringAnswerRepository.save(answer);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent ingest of the same answer — the unique constraint already dedups it.
            }
        }
    }
}
