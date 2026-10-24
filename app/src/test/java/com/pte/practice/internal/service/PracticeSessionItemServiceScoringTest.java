package com.pte.practice.internal.service;

import com.pte.attempt.ResponseConfidence;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionItemStatus;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.scoring.ScoringService;
import com.pte.shared.practice.PracticeResponseMediaValidator;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** How a submitted answer reaches a scorer: inline for objective types, queued for AI types. */
class PracticeSessionItemServiceScoringTest {

    private static final Instant NOW = Instant.parse("2026-10-24T02:00:00Z");

    private final PracticeSessionItemRepository itemRepository = mock(PracticeSessionItemRepository.class);
    private final PracticeAnswerValidationService validation = mock(PracticeAnswerValidationService.class);
    private final ScoringService scoringService = mock(ScoringService.class);
    private final PracticeScoringStateService stateService = mock(PracticeScoringStateService.class);
    private final PracticeScoringRequestFactory requestFactory = mock(PracticeScoringRequestFactory.class);
    private final PracticeScoringRequest request = mock(PracticeScoringRequest.class);
    private final PracticeAiEvaluation evaluation = mock(PracticeAiEvaluation.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("STUDENT"));

    private PracticeSessionItemService service;
    private PracticeSession session;
    private PracticeSessionItem item;

    @BeforeEach
    void setUp() {
        service = new PracticeSessionItemService(itemRepository, null, validation,
                mock(PracticeResponseMediaValidator.class), null, null, scoringService, stateService,
                requestFactory);
        session = new PracticeSession();
        session.setTenantId(UUID.randomUUID());
        item = new PracticeSessionItem();
        item.setTaskCode("MC_SINGLE_ANSWER_READING");
        item.setRendererKey("MC_READING_SINGLE_V1");
        item.setAnswerSchemaVersion(1);
        item.setObjectiveOptionsJson("[{\"text\":\"a\",\"correct\":true,\"orderIndex\":0}]");
        item.setStatus(PracticeSessionItemStatus.PENDING);
        when(requestFactory.build(item, session.getTenantId())).thenReturn(request);
    }

    @Test
    void objectiveAnswerIsScoredInsideTheCallAndFinal() {
        route(PracticeScoringRoute.OBJECTIVE);
        when(scoringService.scorePracticeAnswer(request)).thenReturn(evaluation);

        answer("{\"selectedOption\":\"a\"}");

        verify(stateService).recordInline(item, evaluation, NOW);
        verify(stateService, never()).startScoring(any(), any(), any());
        assertAnswered();
    }

    @Test
    void speechAnswerIsQueuedNotScoredInTheCall() {
        item.setTaskCode("READ_ALOUD");
        item.setRendererKey("READ_ALOUD_V1");
        route(PracticeScoringRoute.AI_SPEECH);

        answer("{\"mediaPublicId\":\"" + UUID.randomUUID() + "\"}");

        verify(stateService).startScoring(session, item, NOW);
        verify(scoringService, never()).scorePracticeAnswer(any());
        assertAnswered();
    }

    @Test
    void writingAnswerIsQueuedNotScoredInTheCall() {
        item.setTaskCode("WRITE_ESSAY");
        route(PracticeScoringRoute.AI_TEXT);

        answer("{\"text\":\"An essay.\"}");

        verify(stateService).startScoring(session, item, NOW);
        verify(scoringService, never()).scorePracticeAnswer(any());
        assertAnswered();
    }

    @Test
    void unscoredTaskIsAnsweredWithoutAnyScoring() {
        item.setTaskCode("PERSONAL_INTRODUCTION");
        route(PracticeScoringRoute.UNSCORED);

        answer("{\"mediaPublicId\":\"" + UUID.randomUUID() + "\"}");

        verifyNoInteractions(stateService);
        verify(scoringService, never()).scorePracticeAnswer(any());
        assertAnswered();
    }

    @Test
    void unsupportedTaskIsRejectedAndStaysPending() {
        route(PracticeScoringRoute.UNSUPPORTED);

        assertThatThrownBy(() -> answer("{\"selectedOption\":\"a\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.PENDING);
    }

    @Test
    void objectiveItemWithoutAnAnswerKeySnapshotFailsClosed() {
        item.setObjectiveOptionsJson(null);
        route(PracticeScoringRoute.OBJECTIVE);

        assertThatThrownBy(() -> answer("{\"selectedOption\":\"a\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.PENDING);
        verifyNoInteractions(stateService);
    }

    @Test
    void objectiveScorerFailureIsServiceUnavailableAndStaysPending() {
        route(PracticeScoringRoute.OBJECTIVE);
        when(scoringService.scorePracticeAnswer(request))
                .thenThrow(new PracticeScoringException(PracticeScoringFailure.INTERNAL));

        assertThatThrownBy(() -> answer("{\"selectedOption\":\"a\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.PENDING);
    }

    @Test
    void objectiveAnswerThatCannotBeCanonicalisedIsUnprocessable() {
        route(PracticeScoringRoute.OBJECTIVE);
        when(requestFactory.build(item, session.getTenantId()))
                .thenThrow(new PracticeScoringException(PracticeScoringFailure.INVALID_PAYLOAD));

        assertThatThrownBy(() -> answer("{\"selectedOption\":\"a\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void rateLimitedSubmissionLeavesTheItemPending() {
        item.setTaskCode("READ_ALOUD");
        route(PracticeScoringRoute.AI_SPEECH);
        doThrow(new PracticeSessionException(HttpStatus.TOO_MANY_REQUESTS, "PRACTICE_SCORING_RATE_LIMITED", "slow"))
                .when(stateService).startScoring(session, item, NOW);

        assertThatThrownBy(() -> answer("{\"mediaPublicId\":\"" + UUID.randomUUID() + "\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.PENDING);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    void anAnsweredItemCannotBeSubmittedAgain() {
        item.setStatus(PracticeSessionItemStatus.ANSWERED);
        item.setScoringStatus(PracticeScoringStatus.SCORED);

        assertThatThrownBy(() -> answer("{\"selectedOption\":\"a\"}"))
                .isInstanceOfSatisfying(PracticeSessionException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(scoringService, stateService);
    }

    @Test
    void recoverScoringRecoversTheLockedScoringItemsOnly() {
        PracticeSessionItem scoring = new PracticeSessionItem();
        scoring.setScoringStatus(PracticeScoringStatus.SCORING);
        session.setId(7L);
        when(itemRepository.findScoringForUpdate(7L)).thenReturn(List.of(scoring));

        service.recoverScoring(session, NOW);

        verify(stateService).recover(session, scoring, NOW);
        verify(itemRepository, never()).findByPracticeSessionIdAndDeletedFalseOrderByOrderIndexAsc(any());
    }

    private void route(PracticeScoringRoute route) {
        when(scoringService.practiceScoringRoute(item.getTaskCode())).thenReturn(route);
    }

    private void answer(String payload) {
        service.answer(session, item, new PracticeAnswerRequest(0L, payload, ResponseConfidence.HIGH), caller, NOW);
    }

    private void assertAnswered() {
        assertThat(item.getStatus()).isEqualTo(PracticeSessionItemStatus.ANSWERED);
        assertThat(item.getAnsweredAt()).isEqualTo(NOW);
        verify(itemRepository).saveAndFlush(item);
    }
}
