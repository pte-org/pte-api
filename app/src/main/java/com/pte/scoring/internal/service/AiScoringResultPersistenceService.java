package com.pte.scoring.internal.service;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persists AI results behind the same answer-then-publication lock order used
 * by report publication. A write lands only while the answer still awaits the
 * caller's dispatch token, so a late or replayed job cannot overwrite a newer
 * dispatch or a terminal result.
 */
@Service
public class AiScoringResultPersistenceService {

    private final AiEvaluationRepository answerRepository;
    private final SessionGradingGateway gateway;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionService sessionService;

    public AiScoringResultPersistenceService(AiEvaluationRepository answerRepository,
            SessionGradingGateway gateway,
            JsonMapper jsonMapper, ApplicationEventPublisher eventPublisher, SessionService sessionService) {
        this.answerRepository = answerRepository;
        this.gateway = gateway;
        this.jsonMapper = jsonMapper;
        this.eventPublisher = eventPublisher;
        this.sessionService = sessionService;
    }

    @Transactional(readOnly = true)
    public boolean isPublicationLocked(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }

    @Transactional
    public boolean persistAiScore(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, AiScoreResult result) {
        // Sorted so the stored JSON is stable whatever map the adapter returned.
        String criteriaScoresJson = result.subScores() == null
                ? null : jsonMapper.writeValueAsString(new TreeMap<>(result.subScores()));
        return applyIfAwaiting(answerPublicId, attemptPublicId, tenantId, sessionPublicId, dispatchToken,
                answer -> {
                    answer.markAiScored(result.rawScore(), result.providerCategory(), result.provider(),
                            result.model(), result.providerVersion());
                    answer.recordDetail(criteriaScoresJson, result.feedback(), result.transcript(),
                            result.detailJson(), result.fallback());
                });
    }

    @Transactional
    public boolean markScoringFailed(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, AiFailureReason reason) {
        return applyIfAwaiting(answerPublicId, attemptPublicId, tenantId, sessionPublicId, dispatchToken,
                answer -> {
                    answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);
                    answer.recordFailure(reason);
                });
    }

    private boolean applyIfAwaiting(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, Consumer<AiEvaluation> onAnswer) {
        if (sessionService != null) {
            sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        }
        AiEvaluation answer = answerRepository.findByAnswerPublicIdForUpdate(answerPublicId)
                .filter(row -> row.isAwaitingAiResult(tenantId, sessionPublicId, attemptPublicId, dispatchToken))
                .orElse(null);
        if (answer == null || publicationLocked(tenantId, sessionPublicId)) {
            return false;
        }
        onAnswer.accept(answer);
        answerRepository.save(answer);
        eventPublisher.publishEvent(new ScoringProgressChangedEvent(answer.getTenantId(), answer.getSessionPublicId()));
        return true;
    }

    private boolean publicationLocked(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }
}
