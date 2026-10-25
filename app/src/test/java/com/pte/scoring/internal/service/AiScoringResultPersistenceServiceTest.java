package com.pte.scoring.internal.service;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScoringResultPersistenceServiceTest {

    private static final Instant DISPATCHED_AT = Instant.parse("2026-10-11T10:00:00Z");

    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AiScoringResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AiScoringResultPersistenceService(answerRepository, gateway,
                JsonMapper.builder().build(), eventPublisher, null);
    }

    @Test
    void persistAiScore_locksAnswerBeforePublicationStateAndStoresScoreWithDetail() {
        AiEvaluation answer = dispatchedAnswer();
        long token = answer.getDispatchToken();
        stubAnswerLock(answer);

        boolean persisted = persist(answer, token, new AiScoreResult(72, Map.of("speaking", 70), "feedback",
                AiProviderCategory.REAL, "AZURE_GEMINI", "model", "v1", "the transcript", "{\"words\":3}", true));

        assertThat(persisted).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getRawScore()).isEqualTo(72);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(answer.getCriteriaScoresJson()).isEqualTo("{\"speaking\":70}");
        assertThat(answer.getFeedback()).isEqualTo("feedback");
        assertThat(answer.getTranscript()).isEqualTo("the transcript");
        assertThat(answer.getDetailJson()).isEqualTo("{\"words\":3}");
        assertThat(answer.isFallback()).isTrue();
        assertThat(answer.getFailureReason()).isNull();
        InOrder lockOrder = inOrder(answerRepository, gateway);
        lockOrder.verify(answerRepository).findByAnswerPublicIdForUpdate(answer.getAnswerPublicId());
        lockOrder.verify(gateway).isPublished(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository).save(answer);
        verify(eventPublisher).publishEvent(any(ScoringProgressChangedEvent.class));
    }

    @Test
    void persistAiScore_rescoreOfAFailedAnswerReplacesTheFailureOnTheSameRow() {
        AiEvaluation answer = dispatchedAnswer();
        answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        answer.recordFailure(AiFailureReason.PROVIDER_TIMEOUT);
        long rescoreToken = answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        stubAnswerLock(answer);

        boolean persisted = persist(answer, rescoreToken, result());

        assertThat(persisted).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getFailureReason()).isNull();
        assertThat(answer.getFeedback()).isEqualTo("feedback");
    }

    @Test
    void persistAiScore_doesNotWriteWhenPublicationHasLockedSession() {
        AiEvaluation answer = dispatchedAnswer();
        stubAnswerLock(answer);
        when(gateway.isPublished(answer.getTenantId(), answer.getSessionPublicId())).thenReturn(true);

        boolean persisted = persist(answer, answer.getDispatchToken(), result());

        assertThat(persisted).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void persistAiScore_supersededDispatchTokenIsIgnored() {
        AiEvaluation answer = dispatchedAnswer();
        long oldToken = answer.getDispatchToken();
        answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        stubAnswerLock(answer);

        boolean persisted = persist(answer, oldToken, result());

        assertThat(persisted).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(gateway, never()).isPublished(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void persistAiScore_doesNotOverwriteAnAlreadyTerminalAnswer() {
        AiEvaluation answer = dispatchedAnswer();
        long token = answer.getDispatchToken();
        answer.markScored(30);
        stubAnswerLock(answer);

        boolean persisted = persist(answer, token, result());

        assertThat(persisted).isFalse();
        assertThat(answer.getRawScore()).isEqualTo(30);
        verify(gateway, never()).isPublished(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void persistAiScore_ignoresAnswerFromAnotherTenantOrSession() {
        AiEvaluation answer = dispatchedAnswer();
        stubAnswerLock(answer);

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                UUID.randomUUID(), answer.getSessionPublicId(), answer.getDispatchToken(), result());

        assertThat(persisted).isFalse();
        verify(gateway, never()).isPublished(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_storesTheReasonOnTheEvaluation() {
        AiEvaluation answer = dispatchedAnswer();
        stubAnswerLock(answer);

        boolean changed = fail(answer, answer.getDispatchToken(), AiFailureReason.PROVIDER_TIMEOUT);

        assertThat(changed).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORING_FAILED);
        verify(answerRepository).save(answer);
        assertThat(answer.getFailureReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT);
        verify(eventPublisher).publishEvent(any(ScoringProgressChangedEvent.class));
    }

    @Test
    void markScoringFailed_supersededDispatchTokenIsIgnored() {
        AiEvaluation answer = dispatchedAnswer();
        long oldToken = answer.getDispatchToken();
        answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        stubAnswerLock(answer);

        boolean changed = fail(answer, oldToken, AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_doesNotChangeTerminalScore() {
        AiEvaluation answer = dispatchedAnswer();
        long token = answer.getDispatchToken();
        answer.markScored(88);
        stubAnswerLock(answer);

        boolean changed = fail(answer, token, AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        assertThat(answer.getRawScore()).isEqualTo(88);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_doesNotChangeInFlightAnswerAfterPublication() {
        AiEvaluation answer = dispatchedAnswer();
        stubAnswerLock(answer);
        when(gateway.isPublished(answer.getTenantId(), answer.getSessionPublicId())).thenReturn(true);

        boolean changed = fail(answer, answer.getDispatchToken(), AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_ignoresAnswerWhenDeadLetterAttemptDoesNotMatch() {
        AiEvaluation answer = dispatchedAnswer();
        stubAnswerLock(answer);

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), UUID.randomUUID(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        verify(gateway, never()).isPublished(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    private boolean persist(AiEvaluation answer, long token, AiScoreResult result) {
        return service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), token, result);
    }

    private boolean fail(AiEvaluation answer, long token, AiFailureReason reason) {
        return service.markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), token, reason);
    }

    private void stubAnswerLock(AiEvaluation answer) {
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
    }

    private AiEvaluation dispatchedAnswer() {
        AiEvaluation answer = new AiEvaluation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "READ_ALOUD", "SPEAKING",
                ScoringMethod.AI_SPEECH);
        answer.markAiDispatched(DISPATCHED_AT);
        return answer;
    }

    private AiScoreResult result() {
        return new AiScoreResult(72, Map.of("speaking", 70), "feedback",
                AiProviderCategory.REAL, "provider", "model", "v1");
    }
}
