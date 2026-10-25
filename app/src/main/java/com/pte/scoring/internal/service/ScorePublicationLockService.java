package com.pte.scoring.internal.service;

import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.session.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates report publication against score writes. Publication locks the session row first (every score
 * mutation takes the same lock), then the answer rows, then marks the session published, so a score write either
 * lands before the publication or is refused after it.
 */
@Service
public class ScorePublicationLockService {

    private final AiEvaluationRepository answerRepository;
    private final SessionService sessionService;
    private final SessionGradingGateway gateway;
    private final ScoringReviewReadQueryService readQueryService;

    public ScorePublicationLockService(AiEvaluationRepository answerRepository, SessionService sessionService,
            SessionGradingGateway gateway, ScoringReviewReadQueryService readQueryService) {
        this.answerRepository = answerRepository;
        this.sessionService = sessionService;
        this.gateway = gateway;
        this.readQueryService = readQueryService;
    }

    @Transactional
    public ReportPublicationScoringView lockForPublication(UUID tenantId, UUID sessionPublicId,
            UUID proposedPublicationPublicId) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        answerRepository.findSessionAnswersForUpdate(sessionPublicId, tenantId);
        UUID publicationPublicId = gateway.publication(tenantId, sessionPublicId)
                .map(SessionGradingGateway.Publication::publicationPublicId).orElse(proposedPublicationPublicId);
        gateway.lockPublication(tenantId, sessionPublicId, publicationPublicId, Instant.now());

        List<ReportScoringAnswerView> inputs = readQueryService.findReportInputs(tenantId, sessionPublicId);
        return new ReportPublicationScoringView(publicationPublicId, inputs);
    }

    public boolean isPublished(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }

    public void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        if (isPublished(tenantId, sessionPublicId)) {
            throw new ScoreSourceSelectionException(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK,
                    null, ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE);
        }
    }
}
