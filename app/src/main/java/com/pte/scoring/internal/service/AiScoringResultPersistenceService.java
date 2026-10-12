package com.pte.scoring.internal.service;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringAnswerAiDetail;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.ScoringAnswerAiDetailRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
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

    private final ScoringAnswerRepository answerRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ScoringAnswerAiDetailRepository detailRepository;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AiScoringResultPersistenceService(ScoringAnswerRepository answerRepository,
            ScoringSessionStateRepository sessionStateRepository, ScoringAnswerAiDetailRepository detailRepository,
            JsonMapper jsonMapper, ApplicationEventPublisher eventPublisher) {
        this.answerRepository = answerRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.detailRepository = detailRepository;
        this.jsonMapper = jsonMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public boolean isPublicationLocked(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }

    @Transactional
    public boolean persistAiScore(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, AiScoreResult result) {
        // Sorted so the stored JSON is stable whatever map the adapter returned.
        String criteriaScoresJson = result.subScores() == null
                ? null : jsonMapper.writeValueAsString(new TreeMap<>(result.subScores()));
        return applyIfAwaiting(answerPublicId, attemptPublicId, tenantId, sessionPublicId, dispatchToken,
                answer -> answer.markAiScored(result.rawScore(), result.providerCategory(), result.provider(),
                        result.model(), result.providerVersion()),
                detail -> detail.recordResult(criteriaScoresJson, result.feedback(), result.transcript(),
                        result.detailJson(), result.fallback()));
    }

    @Transactional
    public boolean markScoringFailed(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, AiFailureReason reason) {
        return applyIfAwaiting(answerPublicId, attemptPublicId, tenantId, sessionPublicId, dispatchToken,
                answer -> answer.setStatus(ScoringAnswerStatus.SCORING_FAILED),
                detail -> detail.recordFailure(reason));
    }

    private boolean applyIfAwaiting(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            long dispatchToken, Consumer<ScoringAnswer> onAnswer, Consumer<ScoringAnswerAiDetail> onDetail) {
        ScoringAnswer answer = answerRepository.findByAnswerPublicIdForUpdate(answerPublicId)
                .filter(row -> row.isAwaitingAiResult(tenantId, sessionPublicId, attemptPublicId, dispatchToken))
                .orElse(null);
        if (answer == null || publicationLocked(tenantId, sessionPublicId)) {
            return false;
        }
        onAnswer.accept(answer);
        answerRepository.save(answer);
        // A rescore reuses the answer's detail row, so a success replaces the earlier failure reason.
        ScoringAnswerAiDetail detail = detailRepository.findByAnswerPublicId(answerPublicId)
                .orElseGet(() -> new ScoringAnswerAiDetail(answerPublicId, answer.getTenantId()));
        onDetail.accept(detail);
        detailRepository.save(detail);
        eventPublisher.publishEvent(new ScoringProgressChangedEvent(answer.getTenantId(), answer.getSessionPublicId()));
        return true;
    }

    private boolean publicationLocked(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findForUpdate(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }
}
