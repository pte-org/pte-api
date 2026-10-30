package com.pte.practice.internal.service;

import com.pte.practice.internal.config.PracticeScoringProperties;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.messaging.PracticeScoringDispatcher;
import com.pte.practice.internal.messaging.PracticeScoringJob;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeScoringStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Mock
    private PracticeResponseRepository itemRepository;
    @Mock
    private PracticeScoringDispatcher dispatcher;

    private PracticeScoringProperties properties;
    private PracticeScoringStateService service;
    private PracticeSession session;
    private PracticeResponse item;

    @BeforeEach
    void setUp() {
        properties = new PracticeScoringProperties();
        service = new PracticeScoringStateService(itemRepository, dispatcher, properties,
                JsonMapper.builder().build());
        session = new PracticeSession();
        session.setStudentPublicId(UUID.randomUUID());
        session.setTenantId(UUID.randomUUID());
        item = new PracticeResponse();
        item.setPublicId(UUID.randomUUID());
        item.setTaskCode("READ_ALOUD");
    }

    @Test
    void startScoringMarksTheItemAndDispatchesTheCurrentToken() {
        service.startScoring(session, item, NOW);

        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORING);
        assertThat(item.getScoringAttempts()).isEqualTo(1);
        assertThat(item.getScoringRequestedAt()).isEqualTo(NOW);
        assertThat(item.getScoringDispatchedAt()).isEqualTo(NOW);
        assertThat(item.getScoringDispatchToken()).isNotNull();
        ArgumentCaptor<PracticeScoringJob> job = ArgumentCaptor.forClass(PracticeScoringJob.class);
        verify(dispatcher).dispatchAfterCommit(job.capture());
        assertThat(job.getValue().itemPublicId()).isEqualTo(item.getPublicId());
        assertThat(job.getValue().tenantId()).isEqualTo(session.getTenantId());
        assertThat(job.getValue().dispatchToken()).isEqualTo(item.getScoringDispatchToken());
    }

    @Test
    void startScoringOverTheUserRateLimitIsRejectedWithoutDispatching() {
        when(itemRepository.countScoringRequestsSince(any(), any())).thenReturn((long) properties.getRateLimitPerMinute());

        assertThatThrownBy(() -> service.startScoring(session, item, NOW))
                .isInstanceOf(PracticeSessionException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SCORING_RATE_LIMITED");
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.NONE);
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void completeWithTheCurrentTokenStoresTheResultAndFinalisesTheItem() {
        UUID token = scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        boolean written = service.complete(item.getPublicId(), token, evaluation(82));

        assertThat(written).isTrue();
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORED);
        assertThat(item.getAiScore()).isEqualTo(82);
        assertThat(item.getAiScoreStatus()).isEqualTo("UNVALIDATED");
        assertThat(item.getAiFeedback()).isEqualTo("Good pacing.");
        assertThat(item.getAiTranscript()).isEqualTo("hello world");
        assertThat(item.getAiSubScoresJson()).contains("\"content\":70");
        assertThat(item.getScoringDispatchToken()).isNull();
        verify(itemRepository).saveAndFlush(item);
    }

    @Test
    void completeWithAStaleTokenWritesNothing() {
        scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        boolean written = service.complete(item.getPublicId(), UUID.randomUUID(), evaluation(82));

        assertThat(written).isFalse();
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORING);
        assertThat(item.getAiScore()).isNull();
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    void aScoredItemIsFinalEvenForTheSameToken() {
        UUID token = scoringItem(NOW);
        item.setScoringStatus(PracticeScoringStatus.SCORED);
        item.setAiScore(90);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        assertThat(service.complete(item.getPublicId(), token, evaluation(10))).isFalse();
        assertThat(item.getAiScore()).isEqualTo(90);
    }

    @Test
    void aConcurrentWriteSurfacesAsAnOptimisticLockFailureForTheWorkerToDrop() {
        UUID token = scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));
        when(itemRepository.saveAndFlush(item)).thenThrow(new OptimisticLockingFailureException("race"));

        assertThatThrownBy(() -> service.complete(item.getPublicId(), token, evaluation(70)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void failWithTheCurrentTokenRecordsARetryableFailure() {
        UUID token = scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        assertThat(service.fail(item.getPublicId(), token, PracticeScoringFailure.PROVIDER_UNAVAILABLE)).isTrue();

        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.FAILED);
        assertThat(item.getScoringFailureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(service.resultOf(item).retryable()).isTrue();
    }

    @Test
    void failWithAStaleTokenWritesNothing() {
        scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        assertThat(service.fail(item.getPublicId(), UUID.randomUUID(), PracticeScoringFailure.INTERNAL)).isFalse();
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORING);
    }

    @Test
    void aRejectedProviderFailureIsNotRetryable() {
        UUID token = scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));
        service.fail(item.getPublicId(), token, PracticeScoringFailure.PROVIDER_REJECTED);

        assertThat(service.resultOf(item).retryable()).isFalse();
        assertThatThrownBy(() -> service.retry(session, item, NOW.plusSeconds(5)))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SCORING_RETRY_NOT_ALLOWED");
    }

    @Test
    void recoverFailsAnItemStuckPastTheMaxAge() {
        scoringItem(NOW.minusSeconds(properties.getMaxAgeSeconds() + 1L));

        service.recover(session, item, NOW);

        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.FAILED);
        assertThat(item.getScoringFailureCode()).isEqualTo("TIMED_OUT");
        assertThat(service.resultOf(item).retryable()).isTrue();
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void recoverRedispatchesAStaleItemWithANewTokenExactlyOnce() {
        UUID oldToken = scoringItem(NOW.minusSeconds(properties.getRedispatchAfterSeconds() + 1L));

        service.recover(session, item, NOW);

        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORING);
        assertThat(item.getScoringDispatchToken()).isNotEqualTo(oldToken);
        assertThat(item.getScoringRedispatchCount()).isEqualTo(1);
        assertThat(item.getScoringDispatchedAt()).isEqualTo(NOW);
        verify(dispatcher).dispatchAfterCommit(any());

        item.setScoringDispatchedAt(NOW.minusSeconds(properties.getRedispatchAfterSeconds() + 1L));
        service.recover(session, item, NOW);
        verify(dispatcher).dispatchAfterCommit(any()); // still only the first re-dispatch
    }

    @Test
    void recoverLeavesAFreshItemAlone() {
        UUID token = scoringItem(NOW.minusSeconds(5));

        service.recover(session, item, NOW);

        assertThat(item.getScoringDispatchToken()).isEqualTo(token);
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void retryOfAFailedItemStartsANewAttempt() {
        UUID oldToken = scoringItem(NOW.minusSeconds(60));
        item.setScoringStatus(PracticeScoringStatus.FAILED);
        item.setScoringFailureCode("PROVIDER_UNAVAILABLE");

        PracticeItemResultResponse result = service.retry(session, item, NOW);

        assertThat(result.state()).isEqualTo(PracticeScoringStatus.SCORING);
        assertThat(item.getScoringAttempts()).isEqualTo(2);
        assertThat(item.getScoringDispatchToken()).isNotEqualTo(oldToken);
        assertThat(item.getScoringFailureCode()).isNull();
        assertThat(item.getScoringRedispatchCount()).isZero();
        verify(dispatcher).dispatchAfterCommit(any());
    }

    @Test
    void retryIsRefusedForScoredScoringAndExhaustedItems() {
        item.setScoringStatus(PracticeScoringStatus.SCORED);
        assertThatThrownBy(() -> service.retry(session, item, NOW))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SCORING_RETRY_NOT_ALLOWED");

        item.setScoringStatus(PracticeScoringStatus.FAILED);
        item.setScoringFailureCode("PROVIDER_UNAVAILABLE");
        item.setScoringAttempts(1 + properties.getMaxRetries());
        assertThatThrownBy(() -> service.retry(session, item, NOW))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SCORING_RETRY_NOT_ALLOWED");
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void retryWhileAlreadyScoringReturnsTheCurrentResultWithoutAnotherDispatch() {
        UUID token = scoringItem(NOW.minusSeconds(3));

        PracticeItemResultResponse result = service.retry(session, item, NOW);

        assertThat(result.state()).isEqualTo(PracticeScoringStatus.SCORING);
        assertThat(item.getScoringDispatchToken()).isEqualTo(token);
        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void retryIsRateLimitedLikeAFirstSubmission() {
        item.setScoringStatus(PracticeScoringStatus.FAILED);
        item.setScoringFailureCode("PROVIDER_UNAVAILABLE");
        item.setScoringAttempts(1);
        when(itemRepository.countScoringRequestsSince(any(), any())).thenReturn(1_000L);

        assertThatThrownBy(() -> service.retry(session, item, NOW))
                .hasFieldOrPropertyWithValue("code", "PRACTICE_SCORING_RATE_LIMITED");
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.FAILED);
    }

    @Test
    void inlineObjectiveResultFillsBothResultSetsAndFinalises() {
        service.recordInline(item, evaluation(100), NOW);

        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORED);
        assertThat(item.getAiScore()).isEqualTo(100);
        assertThat(item.getObjectiveScore()).isEqualTo(100);
        assertThat(item.getObjectiveEvaluatedAt()).isEqualTo(NOW);
        assertThat(item.getAiResultHash()).isEqualTo("hash");
    }

    @Test
    void claimRotatesTheTokenSoARedeliveredJobCannotRunAgain() {
        UUID jobToken = scoringItem(NOW.minusSeconds(2));
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        PracticeResponse claimed = service.claim(item.getPublicId(), jobToken, NOW);

        assertThat(claimed).isSameAs(item);
        assertThat(item.getScoringDispatchToken()).isNotNull().isNotEqualTo(jobToken);
        assertThat(item.getScoringDispatchedAt()).isEqualTo(NOW);
        assertThat(item.getScoringStatus()).isEqualTo(PracticeScoringStatus.SCORING);
        verify(itemRepository).saveAndFlush(item);
        assertThat(service.claim(item.getPublicId(), jobToken, NOW)).isNull();
    }

    @Test
    void claimOfAStaleOrFinishedItemChangesNothing() {
        UUID staleToken = UUID.randomUUID();
        UUID current = scoringItem(NOW);
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));

        assertThat(service.claim(item.getPublicId(), staleToken, NOW)).isNull();
        assertThat(item.getScoringDispatchToken()).isEqualTo(current);

        item.setScoringStatus(PracticeScoringStatus.SCORED);
        assertThat(service.claim(item.getPublicId(), current, NOW)).isNull();
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    void aClaimedJobIsNotRedispatchedUntilItsOwnWindowHasPassed() {
        UUID jobToken = scoringItem(NOW.minusSeconds(properties.getRedispatchAfterSeconds() + 1L));
        when(itemRepository.findByPublicIdAndDeletedFalse(item.getPublicId())).thenReturn(Optional.of(item));
        service.claim(item.getPublicId(), jobToken, NOW);

        service.recover(session, item, NOW.plusSeconds(10));

        verify(dispatcher, never()).dispatchAfterCommit(any());
    }

    @Test
    void anObjectiveOnlyScoreIsStillReportedAsTheItemScore() {
        item.setScoringStatus(PracticeScoringStatus.SCORED);
        item.setObjectiveScore(100);

        assertThat(service.resultOf(item).score()).isEqualTo(100);
    }

    @Test
    void resultOfNeverCarriesAnythingForAnUnstartedItem() {
        PracticeItemResultResponse result = service.resultOf(item);

        assertThat(result.state()).isEqualTo(PracticeScoringStatus.NONE);
        assertThat(result.score()).isNull();
        assertThat(result.retryable()).isFalse();
        verify(itemRepository, never()).countScoringRequestsSince(any(), any());
    }

    private UUID scoringItem(Instant dispatchedAt) {
        UUID token = UUID.randomUUID();
        item.setScoringStatus(PracticeScoringStatus.SCORING);
        item.setScoringDispatchToken(token);
        item.setScoringAttempts(1);
        item.setScoringRequestedAt(dispatchedAt);
        item.setScoringDispatchedAt(dispatchedAt);
        return token;
    }

    private PracticeAiEvaluation evaluation(int score) {
        return new PracticeAiEvaluation(score, Map.of("content", 70), "UNVALIDATED", "azure", "model", "v1",
                "hello world", "{}", NOW, "hash", "Good pacing.");
    }
}
