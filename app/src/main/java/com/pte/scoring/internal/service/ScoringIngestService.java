package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pulls attempt's canonical submitted answers into scoring's own work-state
 * copy — replaces the old {@code AnswerSubmitted}-event live ingestion with
 * an in-process pull, run right before a host's scoring request is processed
 * (dependency order: attempt ──> scoring, so scoring calls out to attempt,
 * never the reverse). Idempotent by the {@code answer_public_id} unique
 * constraint: an already-ingested answer (any status, including already
 * SCORED) is never re-inserted. Ingesting only makes answers reviewable;
 * scoring still starts only from the host's command.
 *
 * <p>Only the host's scoring request, which holds the session lock, also
 * refreshes the pinned question content of existing rows (status, payload
 * and scores are left untouched). Grading reconcile runs inside an AI
 * worker's commit while holding the cohort lock, so there it inserts new
 * rows only and never updates a row another worker may have locked.
 */
@Service
public class ScoringIngestService {

    private final AttemptService attemptService;
    private final ScoringAnswerRepository scoringAnswerRepository;

    public ScoringIngestService(AttemptService attemptService, ScoringAnswerRepository scoringAnswerRepository) {
        this.attemptService = attemptService;
        this.scoringAnswerRepository = scoringAnswerRepository;
    }

    /** Inserts answers not ingested yet; existing rows are left as they are. */
    @Transactional
    public void ingestForSession(UUID sessionPublicId, UUID tenantId) {
        ingest(sessionPublicId, tenantId, false);
    }

    /** Like {@link #ingestForSession}, and rows ingested before question content was copied pick it up. */
    @Transactional
    public void ingestForScoring(UUID sessionPublicId, UUID tenantId) {
        ingest(sessionPublicId, tenantId, true);
    }

    /**
     * Ingests one submitted attempt right after it commits, so the host can review its answers before
     * scoring. Always its own transaction: it runs after the submitting transaction has completed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ingestSubmittedAttempt(UUID attemptPublicId, UUID tenantId) {
        ingest(attemptService.getSubmittedAnswersForAttempt(attemptPublicId, tenantId), false);
    }

    private void ingest(UUID sessionPublicId, UUID tenantId, boolean refreshQuestionContent) {
        ingest(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId), refreshQuestionContent);
    }

    private void ingest(List<SubmittedAnswerView> views, boolean refreshQuestionContent) {
        for (SubmittedAnswerView view : views) {
            Optional<ScoringAnswer> existing = scoringAnswerRepository.findByAnswerPublicId(view.answerPublicId());
            if (existing.isPresent()) {
                if (refreshQuestionContent && applyQuestionContent(existing.get(), view)) {
                    scoringAnswerRepository.save(existing.get());
                }
                continue;
            }
            ScoringAnswer answer = new ScoringAnswer();
            answer.setAnswerPublicId(view.answerPublicId());
            answer.setAttemptPublicId(view.attemptPublicId());
            answer.setPinnedItemPublicId(view.pinnedItemPublicId());
            answer.setSessionPublicId(view.sessionPublicId());
            answer.setTenantId(view.tenantId());
            answer.setScoreTemplatePublicId(view.scoreTemplatePublicId());
            answer.setTaskType(view.taskType());
            answer.setPayload(view.payload());
            answer.setCorrectAnswerText(view.correctAnswerText());
            answer.setOptionsJson(view.optionsJson());
            answer.setExpired(view.expired());
            applyQuestionContent(answer, view);
            try {
                scoringAnswerRepository.save(answer);
            } catch (DataIntegrityViolationException ex) {
                // Concurrent ingest of the same answer — the unique constraint already dedups it.
            }
        }
    }

    private static boolean applyQuestionContent(ScoringAnswer answer, SubmittedAnswerView view) {
        return answer.applyQuestionContent(view.promptText(), view.referenceAnswerText(), view.imagePromptRef(),
                view.minWordCount(), view.maxWordCount());
    }
}
