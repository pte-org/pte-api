package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Executes a host's "score this session" command (ADR-002 host-gated model —
 * scoring NEVER auto-triggers on submit). Ingests whatever attempt has
 * submitted so far, then for every {@code PENDING} answer in the session:
 * {@link ObjectiveScoringService}-supported types score synchronously;
 * {@link AiScoringDispatcher}-supported types (all ten AI-shaped
 * Speaking/Writing/Listening tasks) get queued to RabbitMQ instead; any other
 * type stays {@code PENDING} — honest completion, not a fake "skipped"
 * status. Callable multiple times for the same session as more answers come
 * in (each call only ingests/scores what's newly PENDING).
 */
@Service
public class ScoringCommandService {

    private final ScoringIngestService scoringIngestService;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ObjectiveScoringService objectiveScoringService;
    private final AiScoringDispatcher aiScoringDispatcher;

    public ScoringCommandService(ScoringIngestService scoringIngestService,
                                 ScoringAnswerRepository scoringAnswerRepository,
                                 ObjectiveScoringService objectiveScoringService,
                                 AiScoringDispatcher aiScoringDispatcher) {
        this.scoringIngestService = scoringIngestService;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.objectiveScoringService = objectiveScoringService;
        this.aiScoringDispatcher = aiScoringDispatcher;
    }

    @Transactional
    public void requestScoring(UUID sessionPublicId, UUID tenantId) {
        scoringIngestService.ingestForSession(sessionPublicId, tenantId);

        List<ScoringAnswer> pending = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndStatus(sessionPublicId, tenantId, ScoringAnswerStatus.PENDING);
        for (ScoringAnswer answer : pending) {
            if (objectiveScoringService.supports(answer.getTaskType())) {
                scoreObjectively(answer);
            } else if (aiScoringDispatcher.supports(answer.getTaskType())) {
                aiScoringDispatcher.dispatch(answer);
            }
            // Any other type: stays PENDING (honest completion).
        }
    }

    private void scoreObjectively(ScoringAnswer answer) {
        int rawScore = objectiveScoringService.score(answer);
        answer.markScored(rawScore);
        scoringAnswerRepository.save(answer);
    }
}
