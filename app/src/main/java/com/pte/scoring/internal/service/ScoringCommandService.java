package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.session.SessionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a host "score this session" command (ADR-002 host-gated model — scoring NEVER auto-triggers on
 * submit). Opens an evaluation for whatever attempt has submitted so far, then works every one that is waiting:
 * {@code OBJECTIVE} answers are scored synchronously, {@code AI_SPEECH}/{@code AI_TEXT} answers are queued to
 * RabbitMQ, and an answer with no scoring method (a task type the exam gives no rule, such as
 * {@code PERSONAL_INTRODUCTION}) stays {@code PENDING} — honest completion, not a fake "skipped" status.
 * Callable many times for the same session as more answers come in: each call scores what is newly PENDING and
 * sends again the AI answers that failed or whose job was lost
 * ({@link AiEvaluationRepository#findScoringWork}). Those rows go straight back to {@code AI_SCORING} under a new
 * dispatch token, never through PENDING.
 */
@Service
public class ScoringCommandService {

    private final ScoringIngestService scoringIngestService;
    private final AiEvaluationRepository evaluationRepository;
    private final ObjectiveScoringService objectiveScoringService;
    private final AiScoringDispatcher aiScoringDispatcher;
    private final AnswerContentReader contentReader;
    private final SessionService sessionService;
    private final ScorePublicationLockService publicationLockService;
    private final ApplicationEventPublisher eventPublisher;

    public ScoringCommandService(ScoringIngestService scoringIngestService,
                                 AiEvaluationRepository evaluationRepository,
                                 ObjectiveScoringService objectiveScoringService,
                                 AiScoringDispatcher aiScoringDispatcher,
                                 AnswerContentReader contentReader,
                                 SessionService sessionService,
                                 ScorePublicationLockService publicationLockService,
                                 ApplicationEventPublisher eventPublisher) {
        this.scoringIngestService = scoringIngestService;
        this.evaluationRepository = evaluationRepository;
        this.objectiveScoringService = objectiveScoringService;
        this.aiScoringDispatcher = aiScoringDispatcher;
        this.contentReader = contentReader;
        this.sessionService = sessionService;
        this.publicationLockService = publicationLockService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void requestScoring(UUID sessionPublicId, UUID tenantId) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        publicationLockService.assertNotPublished(tenantId, sessionPublicId);
        scoringIngestService.ingestForSession(sessionPublicId, tenantId);
        List<AiEvaluation> work = evaluationRepository.findScoringWork(sessionPublicId, tenantId,
                aiScoringDispatcher.staleBefore());
        Map<UUID, SubmittedAnswerView> content = null;
        for (AiEvaluation evaluation : work) {
            ScoringMethod method = evaluation.getScoringMethod();
            if (method == null) {
                continue; // The exam gives this task type no scoring rule — stays PENDING.
            }
            if (objectiveScoringService.supports(method)) {
                if (content == null) {
                    content = contentReader.forSession(sessionPublicId, tenantId);
                }
                scoreObjectively(evaluation, content.get(evaluation.getAnswerPublicId()));
            } else if (aiScoringDispatcher.supports(method)) {
                aiScoringDispatcher.dispatch(evaluation, method);
            }
            // UNSCORED: stays PENDING (honest completion, not an error).
        }
    }

    private void scoreObjectively(AiEvaluation evaluation, SubmittedAnswerView answer) {
        if (answer == null) {
            return;
        }
        evaluation.markScored(objectiveScoringService.score(answer));
        evaluationRepository.save(evaluation);
        eventPublisher.publishEvent(new ScoringProgressChangedEvent(evaluation.getTenantId(),
                evaluation.getSessionPublicId()));
    }
}
