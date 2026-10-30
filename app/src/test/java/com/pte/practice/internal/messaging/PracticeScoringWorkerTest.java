package com.pte.practice.internal.messaging;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.service.PracticeScoringRequestFactory;
import com.pte.practice.internal.service.PracticeScoringStateService;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PracticeScoringWorkerTest {

    private final PracticeScoringStateService stateService = mock(PracticeScoringStateService.class);
    private final PracticeScoringRequestFactory requestFactory = mock(PracticeScoringRequestFactory.class);
    private final ScoringService scoringService = mock(ScoringService.class);
    private final PracticeScoringWorker worker =
            new PracticeScoringWorker(stateService, requestFactory, scoringService,
                    Clock.fixed(Instant.parse("2026-10-24T02:00:00Z"), ZoneOffset.UTC));

    private final UUID itemId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID token = UUID.randomUUID();
    private final UUID claimToken = UUID.randomUUID();
    private final PracticeScoringJob job = new PracticeScoringJob(itemId, tenantId, token);
    private final PracticeResponse item = new PracticeResponse();
    private final PracticeScoringRequest request = mock(PracticeScoringRequest.class);
    private final PracticeAiEvaluation evaluation = mock(PracticeAiEvaluation.class);

    @BeforeEach
    void awaitingItem() {
        item.setScoringDispatchToken(claimToken);
        when(stateService.claim(eq(itemId), eq(token), any())).thenReturn(item);
        when(requestFactory.build(item, tenantId)).thenReturn(request);
    }

    @Test
    void scoresTheItemAndStoresTheResultForTheJobsToken() {
        when(scoringService.scorePracticeAnswer(request)).thenReturn(evaluation);

        worker.handle(job);

        verify(stateService).complete(itemId, claimToken, evaluation);
        verify(stateService, never()).fail(any(), any(), any());
    }

    @Test
    void aStaleJobNeverReachesTheVendor() {
        when(stateService.claim(eq(itemId), eq(token), any())).thenReturn(null);

        worker.handle(job);

        verifyNoInteractions(scoringService, requestFactory);
        verify(stateService, never()).complete(any(), any(), any());
        verify(stateService, never()).fail(any(), any(), any());
    }

    @Test
    void aRedeliveredJobIsSkippedOnceAnotherDeliveryClaimedIt() {
        when(stateService.claim(eq(itemId), eq(token), any())).thenReturn(item).thenReturn(null);
        when(scoringService.scorePracticeAnswer(request)).thenReturn(evaluation);

        worker.handle(job);
        worker.handle(job);

        verify(scoringService, times(1)).scorePracticeAnswer(request);
    }

    @Test
    void aProviderFailureIsRecordedWithItsReason() {
        when(scoringService.scorePracticeAnswer(request))
                .thenThrow(new PracticeScoringException(PracticeScoringFailure.PROVIDER_UNAVAILABLE));

        worker.handle(job);

        verify(stateService).fail(itemId, claimToken, PracticeScoringFailure.PROVIDER_UNAVAILABLE);
        verify(stateService, never()).complete(any(), any(), any());
    }

    @Test
    void anUnscoreableAnswerFailsBeforeCallingTheVendor() {
        when(requestFactory.build(item, tenantId))
                .thenThrow(new PracticeScoringException(PracticeScoringFailure.INVALID_PAYLOAD));

        worker.handle(job);

        verify(stateService).fail(itemId, claimToken, PracticeScoringFailure.INVALID_PAYLOAD);
        verifyNoInteractions(scoringService);
    }

    @Test
    void aConcurrentWriteDropsTheResultInsteadOfRetryingTheVendor() {
        when(scoringService.scorePracticeAnswer(request)).thenReturn(evaluation);
        when(stateService.complete(itemId, claimToken, evaluation))
                .thenThrow(new OptimisticLockingFailureException("raced"));

        assertThatCode(() -> worker.handle(job)).doesNotThrowAnyException();

        verify(stateService, never()).fail(any(), any(), any());
    }

    @Test
    void anUnexpectedErrorIsRethrownSoTheMessageGoesToTheDeadLetterQueue() {
        when(scoringService.scorePracticeAnswer(request)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> worker.handle(job)).isInstanceOf(IllegalStateException.class);

        verify(stateService, never()).complete(any(), any(), any());
    }

    @Test
    void aDeadLetteredJobLeavesTheItemFailedAsInternal() {
        worker.handleDeadLetter(job);

        verify(stateService).fail(itemId, token, PracticeScoringFailure.INTERNAL);
    }

    @Test
    void aDeadLetteredJobForAnAlreadyFinishedItemChangesNothing() {
        when(stateService.fail(itemId, token, PracticeScoringFailure.INTERNAL)).thenReturn(false);

        assertThatCode(() -> worker.handleDeadLetter(job)).doesNotThrowAnyException();
    }
}
