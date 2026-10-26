package com.pte.practice.internal.service;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.ScoringService;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Routes a submitted practice answer to inline objective scoring, the AI worker queue, or no scoring. */
class PracticeAnswerScoringRouter {

    private final ScoringService scoringService;
    private final PracticeScoringStateService scoringStateService;
    private final PracticeScoringRequestFactory scoringRequestFactory;

    PracticeAnswerScoringRouter(ScoringService scoringService, PracticeScoringStateService scoringStateService,
            PracticeScoringRequestFactory scoringRequestFactory) {
        this.scoringService = scoringService;
        this.scoringStateService = scoringStateService;
        this.scoringRequestFactory = scoringRequestFactory;
    }

    /**
     * Same split as the exam: objective types are scored inside the call, speech and writing are queued for the
     * worker, tasks with no score (Personal Introduction) are only recorded.
     */
    void scoreOrQueue(PracticeSession session, PracticeResponse item, Instant now) {
        if (scoringService == null || scoringStateService == null) {
            return;
        }
        switch (scoringService.practiceScoringRoute(item.getTaskCode())) {
            case OBJECTIVE -> scoreObjective(session, item, now);
            case AI_SPEECH, AI_TEXT -> scoringStateService.startScoring(session, item, now);
            case UNSCORED -> { }
            case UNSUPPORTED -> throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME,
                    PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME_MESSAGE);
        }
    }

    private void scoreObjective(PracticeSession session, PracticeResponse item, Instant now) {
        // Items created before the answer-key snapshot migration cannot be scored
        // safely. Fail closed instead of persisting an incorrect zero.
        if (item.getObjectiveOptionsJson() == null || item.getObjectiveOptionsJson().isBlank()) {
            throw deterministicScoringUnavailable();
        }
        PracticeAiEvaluation evaluation;
        try {
            evaluation = scoringService.scorePracticeAnswer(scoringRequestFactory.build(item, session.getTenantId()));
        } catch (PracticeScoringException exception) {
            if (exception.failure() == PracticeScoringFailure.INVALID_PAYLOAD) {
                throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                        PracticeConstants.PRACTICE_ANSWER_INVALID, PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
            }
            throw deterministicScoringUnavailable();
        }
        scoringStateService.recordInline(item, evaluation, now);
    }

    private PracticeSessionException deterministicScoringUnavailable() {
        return new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                PracticeConstants.PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE,
                PracticeConstants.PRACTICE_DETERMINISTIC_SCORING_UNAVAILABLE_MESSAGE);
    }
}
