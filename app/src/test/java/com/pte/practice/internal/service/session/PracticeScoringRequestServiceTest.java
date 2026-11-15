package com.pte.practice.internal.service.session;

import com.pte.practice.internal.config.PracticeScoringProperties;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestScoringResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.practice.internal.service.PracticeScoringStateService;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.scoring.ScoringService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Chấm điểm": one answer of a task session, or a whole finished mock test; an answer is scored once. */
class PracticeScoringRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final PracticeSessionRepository sessionRepository = mock(PracticeSessionRepository.class);
    private final PracticeResponseService itemService = mock(PracticeResponseService.class);
    private final PracticeResponseRepository itemRepository = mock(PracticeResponseRepository.class);
    private final PracticeScoringStateService stateService = mock(PracticeScoringStateService.class);
    private final ScoringService scoringService = mock(ScoringService.class);
    private final PracticeScoringProperties properties = new PracticeScoringProperties();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"));
    private final PracticeSession session = new PracticeSession();
    private PracticeScoringRequestService service;

    @BeforeEach
    void setUp() {
        service = new PracticeScoringRequestService(sessionRepository, mock(PracticeEntitlementService.class), itemService,
                itemRepository, stateService, scoringService, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        session.setPublicId(UUID.randomUUID());
        session.setKind(PracticeSessionKind.TASK);
        when(sessionRepository.findWithLockByPublicIdAndStudentPublicId(session.getPublicId(), caller.userId()))
                .thenReturn(java.util.Optional.of(session));
        when(scoringService.practiceScoringRoute("READ_ALOUD")).thenReturn(PracticeScoringRoute.AI_SPEECH);
        when(scoringService.practiceScoringRoute("WRITE_ESSAY")).thenReturn(PracticeScoringRoute.AI_TEXT);
        when(scoringService.practiceScoringRoute("MC_READING_SINGLE")).thenReturn(PracticeScoringRoute.OBJECTIVE);
    }

    @Test
    void anUnscoredAnswerIsSentToTheScorer() {
        PracticeResponse item = item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.NONE);

        service.requestItem(session.getPublicId(), item.getPublicId(), caller);

        verify(stateService).startScoring(session, item, NOW);
        verify(itemRepository).saveAndFlush(item);
    }

    @Test
    void aScoredAnswerIsNotScoredAgain() {
        PracticeResponse item = item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.SCORED);

        assertThatThrownBy(() -> service.requestItem(session.getPublicId(), item.getPublicId(), caller))
                .isInstanceOfSatisfying(PracticeSessionException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(PracticeConstants.PRACTICE_SCORING_ALREADY_REQUESTED);
                });
        verify(stateService, never()).startScoring(any(), any(), any());
    }

    @Test
    void askingAgainWhileScoringOnlyReportsTheState() {
        PracticeResponse item = item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.SCORING);

        service.requestItem(session.getPublicId(), item.getPublicId(), caller);

        verify(stateService, never()).startScoring(any(), any(), any());
        verify(itemService).resultOf(item);
    }

    @Test
    void aFailedAnswerGoesThroughTheRetryRules() {
        PracticeResponse item = item("WRITE_ESSAY", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.FAILED);

        service.requestItem(session.getPublicId(), item.getPublicId(), caller);

        verify(itemService).retryScoring(session, item, NOW);
    }

    @Test
    void objectiveAndUnsubmittedAnswersHaveNoAiScoreToAskFor() {
        PracticeResponse objective = item("MC_READING_SINGLE", PracticeResponseStatus.ANSWERED,
                PracticeScoringStatus.SCORED);
        PracticeResponse pending = item("READ_ALOUD", PracticeResponseStatus.PENDING, PracticeScoringStatus.NONE);

        for (PracticeResponse item : List.of(objective, pending)) {
            assertThatThrownBy(() -> service.requestItem(session.getPublicId(), item.getPublicId(), caller))
                    .isInstanceOfSatisfying(PracticeSessionException.class, ex -> assertThat(ex.getCode())
                            .isEqualTo(PracticeConstants.PRACTICE_SCORING_NOT_AVAILABLE));
        }
    }

    @Test
    void aMockTestIsScoredOnlyAsAWholeOnceItIsSubmitted() {
        session.setKind(PracticeSessionKind.MOCK_TEST);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        PracticeResponse item = item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.NONE);

        assertThatThrownBy(() -> service.requestItem(session.getPublicId(), item.getPublicId(), caller))
                .isInstanceOf(PracticeSessionException.class);
        assertThatThrownBy(() -> service.requestMockTest(session.getPublicId(), caller))
                .isInstanceOfSatisfying(PracticeSessionException.class, ex -> assertThat(ex.getCode())
                        .isEqualTo(PracticeConstants.PRACTICE_MOCK_TEST_NOT_FINISHED));
    }

    @Test
    void aFinishedMockTestQueuesEveryUnscoredAiAnswerInTurns() {
        session.setKind(PracticeSessionKind.MOCK_TEST);
        session.setStatus(PracticeSessionStatus.COMPLETED);
        properties.setConcurrency(2);
        List<PracticeResponse> items = List.of(
                item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.NONE),
                item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.NONE),
                item("WRITE_ESSAY", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.NONE),
                item("WRITE_ESSAY", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.SCORING),
                item("READ_ALOUD", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.SCORED),
                item("READ_ALOUD", PracticeResponseStatus.SKIPPED, PracticeScoringStatus.NONE),
                item("MC_READING_SINGLE", PracticeResponseStatus.ANSWERED, PracticeScoringStatus.SCORED));
        when(itemService.itemsFor(session)).thenReturn(items);

        PracticeMockTestScoringResponse response = service.requestMockTest(session.getPublicId(), caller);

        assertThat(response.queuedCount()).isEqualTo(3);
        assertThat(response.scoringCount()).isEqualTo(4);
        ArgumentCaptor<Duration> waits = ArgumentCaptor.forClass(Duration.class);
        verify(stateService, times(3)).startQueuedScoring(eq(session), any(), eq(NOW), waits.capture());
        long slot = PracticeConstants.PRACTICE_SCORING_QUEUE_SLOT_SECONDS;
        assertThat(waits.getAllValues()).containsExactly(Duration.ZERO, Duration.ZERO, Duration.ofSeconds(slot));
    }

    private PracticeResponse item(String taskCode, PracticeResponseStatus status, PracticeScoringStatus scoring) {
        PracticeResponse item = new PracticeResponse();
        item.setPublicId(UUID.randomUUID());
        item.setTaskCode(taskCode);
        item.setStatus(status);
        item.setScoringStatus(scoring);
        when(itemService.findForUpdate(session, item.getPublicId())).thenReturn(item);
        return item;
    }
}
