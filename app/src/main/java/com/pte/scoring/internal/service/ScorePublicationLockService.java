package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Coordinates report publication against examiner score writes using a stable answer-then-state lock order. */
@Service
public class ScorePublicationLockService {

    private final ScoringAnswerRepository answerRepository;
    private final ScoringSessionStateRepository stateRepository;
    private final ScoringReviewReadQueryService readQueryService;

    public ScorePublicationLockService(ScoringAnswerRepository answerRepository,
            ScoringSessionStateRepository stateRepository,
            ScoringReviewReadQueryService readQueryService) {
        this.answerRepository = answerRepository;
        this.stateRepository = stateRepository;
        this.readQueryService = readQueryService;
    }

    @Transactional
    public ReportPublicationScoringView lockForPublication(UUID tenantId, UUID sessionPublicId,
            UUID proposedPublicationPublicId) {
        List<ScoringAnswer> lockedAnswers = answerRepository.findSessionAnswersForUpdate(sessionPublicId, tenantId);
        ScoringSessionState state = stateRepository.findForUpdate(tenantId, sessionPublicId)
                .orElseGet(() -> stateRepository.saveAndFlush(new ScoringSessionState(tenantId, sessionPublicId)));
        UUID publicationPublicId = state.getPublicationPublicId() == null
                ? proposedPublicationPublicId : state.getPublicationPublicId();
        state.lockForPublication(publicationPublicId, Instant.now());
        stateRepository.saveAndFlush(state);

        List<ReportScoringAnswerView> inputs = readQueryService.findReportInputs(tenantId, sessionPublicId);
        return new ReportPublicationScoringView(publicationPublicId, inputs);
    }

    public boolean isPublished(UUID tenantId, UUID sessionPublicId) {
        return stateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }

    public void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        if (isPublished(tenantId, sessionPublicId)) {
            throw new ScoreSourceSelectionException(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK,
                    null, ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE);
        }
    }
}
