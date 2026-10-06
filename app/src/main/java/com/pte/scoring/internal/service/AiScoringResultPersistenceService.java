package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Persists AI results behind the same answer-then-publication lock order used by report publication. */
@Service
public class AiScoringResultPersistenceService {

    private final ScoringAnswerRepository answerRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public AiScoringResultPersistenceService(ScoringAnswerRepository answerRepository,
            ScoringSessionStateRepository sessionStateRepository, ApplicationEventPublisher eventPublisher) {
        this.answerRepository = answerRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Compatibility constructor for focused persistence tests. */
    public AiScoringResultPersistenceService(ScoringAnswerRepository answerRepository,
            ScoringSessionStateRepository sessionStateRepository) {
        this(answerRepository, sessionStateRepository, null);
    }

    @Transactional(readOnly = true)
    public boolean isPublicationLocked(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }

    @Transactional
    public boolean persistAiScore(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId,
            AiScoreResult result) {
        ScoringAnswer answer = lockAnswer(answerPublicId, attemptPublicId, tenantId, sessionPublicId);
        if (answer == null || isTerminal(answer) || publicationLocked(tenantId, sessionPublicId)) {
            return false;
        }
        answer.markAiScored(result.rawScore(), result.providerCategory(), result.provider(),
                result.model(), result.providerVersion());
        answerRepository.save(answer);
        publishProgress(answer);
        return true;
    }

    @Transactional
    public boolean markScoringFailed(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId) {
        ScoringAnswer answer = lockAnswer(answerPublicId, attemptPublicId, tenantId, sessionPublicId);
        if (answer == null || isTerminal(answer) || publicationLocked(tenantId, sessionPublicId)) {
            return false;
        }
        answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        answerRepository.save(answer);
        publishProgress(answer);
        return true;
    }

    private void publishProgress(ScoringAnswer answer) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.pte.scoring.ScoringProgressChangedEvent(
                    answer.getTenantId(), answer.getSessionPublicId()));
        }
    }

    private ScoringAnswer lockAnswer(UUID answerPublicId, UUID attemptPublicId, UUID tenantId, UUID sessionPublicId) {
        return answerRepository.findByAnswerPublicIdForUpdate(answerPublicId)
                .filter(answer -> answer.getAttemptPublicId().equals(attemptPublicId)
                        && answer.getTenantId().equals(tenantId)
                        && answer.getSessionPublicId().equals(sessionPublicId))
                .orElse(null);
    }

    private boolean publicationLocked(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findForUpdate(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }

    private boolean isTerminal(ScoringAnswer answer) {
        return answer.getStatus() == ScoringAnswerStatus.SCORED
                || answer.getStatus() == ScoringAnswerStatus.SCORING_FAILED;
    }
}
