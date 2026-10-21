package com.pte.scoring.internal.service;

import com.pte.scoring.ScoringProgressChangedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringAnswerAiDetail;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.ScoringAnswerAiDetailRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
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
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private ScoringAnswerAiDetailRepository detailRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AiScoringResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AiScoringResultPersistenceService(answerRepository, sessionStateRepository, detailRepository,
                JsonMapper.builder().build(), eventPublisher);
    }

    @Test
    void persistAiScore_locksAnswerBeforePublicationStateAndStoresScoreWithDetail() {
        ScoringAnswer answer = dispatchedAnswer();
        long token = answer.getDispatchToken();
        stubAnswerLock(answer);
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.empty());

        boolean persisted = persist(answer, token, new AiScoreResult(72, Map.of("speaking", 70), "feedback",
                AiProviderCategory.REAL, "AZURE_GEMINI", "model", "v1", "the transcript", "{\"words\":3}", true));

        assertThat(persisted).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getRawScore()).isEqualTo(72);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        ScoringAnswerAiDetail detail = savedDetail();
        assertThat(detail.getAnswerPublicId()).isEqualTo(answer.getAnswerPublicId());
        assertThat(detail.getTenantId()).isEqualTo(answer.getTenantId());
        assertThat(detail.getCriteriaScoresJson()).isEqualTo("{\"speaking\":70}");
        assertThat(detail.getFeedback()).isEqualTo("feedback");
        assertThat(detail.getTranscript()).isEqualTo("the transcript");
        assertThat(detail.getDetailJson()).isEqualTo("{\"words\":3}");
        assertThat(detail.isFallback()).isTrue();
        assertThat(detail.getFailureReason()).isNull();
        InOrder lockOrder = inOrder(answerRepository, sessionStateRepository, detailRepository);
        lockOrder.verify(answerRepository).findByAnswerPublicIdForUpdate(answer.getAnswerPublicId());
        lockOrder.verify(sessionStateRepository).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        lockOrder.verify(detailRepository).findByAnswerPublicId(answer.getAnswerPublicId());
        verify(answerRepository).save(answer);
        verify(eventPublisher).publishEvent(any(ScoringProgressChangedEvent.class));
    }

    @Test
    void persistAiScore_rescoreOfAFailedAnswerReplacesTheFailureOnTheSameDetailRow() {
        ScoringAnswer answer = dispatchedAnswer();
        answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        long rescoreToken = answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        ScoringAnswerAiDetail existing = new ScoringAnswerAiDetail(answer.getAnswerPublicId(), answer.getTenantId());
        existing.recordFailure(AiFailureReason.PROVIDER_TIMEOUT);
        stubAnswerLock(answer);
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.empty());
        when(detailRepository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(existing));

        boolean persisted = persist(answer, rescoreToken, result());

        assertThat(persisted).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(savedDetail()).isSameAs(existing);
        assertThat(existing.getFailureReason()).isNull();
        assertThat(existing.getFeedback()).isEqualTo("feedback");
    }

    @Test
    void persistAiScore_doesNotWriteWhenPublicationHasLockedSession() {
        ScoringAnswer answer = dispatchedAnswer();
        stubAnswerLock(answer);
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.of(publishedState(answer)));

        boolean persisted = persist(answer, answer.getDispatchToken(), result());

        assertThat(persisted).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(detailRepository, eventPublisher);
    }

    @Test
    void persistAiScore_supersededDispatchTokenIsIgnored() {
        ScoringAnswer answer = dispatchedAnswer();
        long oldToken = answer.getDispatchToken();
        answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        stubAnswerLock(answer);

        boolean persisted = persist(answer, oldToken, result());

        assertThat(persisted).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(detailRepository, eventPublisher);
    }

    @Test
    void persistAiScore_doesNotOverwriteAnAlreadyTerminalAnswer() {
        ScoringAnswer answer = dispatchedAnswer();
        long token = answer.getDispatchToken();
        answer.markScored(30);
        stubAnswerLock(answer);

        boolean persisted = persist(answer, token, result());

        assertThat(persisted).isFalse();
        assertThat(answer.getRawScore()).isEqualTo(30);
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void persistAiScore_ignoresAnswerFromAnotherTenantOrSession() {
        ScoringAnswer answer = dispatchedAnswer();
        stubAnswerLock(answer);

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                UUID.randomUUID(), answer.getSessionPublicId(), answer.getDispatchToken(), result());

        assertThat(persisted).isFalse();
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_storesTheReasonOnTheDetailRow() {
        ScoringAnswer answer = dispatchedAnswer();
        stubAnswerLock(answer);
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.empty());

        boolean changed = fail(answer, answer.getDispatchToken(), AiFailureReason.PROVIDER_TIMEOUT);

        assertThat(changed).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORING_FAILED);
        verify(answerRepository).save(answer);
        ScoringAnswerAiDetail detail = savedDetail();
        assertThat(detail.getAnswerPublicId()).isEqualTo(answer.getAnswerPublicId());
        assertThat(detail.getFailureReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT);
        verify(eventPublisher).publishEvent(any(ScoringProgressChangedEvent.class));
    }

    @Test
    void markScoringFailed_supersededDispatchTokenIsIgnored() {
        ScoringAnswer answer = dispatchedAnswer();
        long oldToken = answer.getDispatchToken();
        answer.markAiDispatched(DISPATCHED_AT.plusSeconds(900));
        stubAnswerLock(answer);

        boolean changed = fail(answer, oldToken, AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(detailRepository);
    }

    @Test
    void markScoringFailed_doesNotChangeTerminalScore() {
        ScoringAnswer answer = dispatchedAnswer();
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
        ScoringAnswer answer = dispatchedAnswer();
        stubAnswerLock(answer);
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.of(publishedState(answer)));

        boolean changed = fail(answer, answer.getDispatchToken(), AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(answerRepository, never()).save(answer);
        verifyNoInteractions(detailRepository);
    }

    @Test
    void markScoringFailed_ignoresAnswerWhenDeadLetterAttemptDoesNotMatch() {
        ScoringAnswer answer = dispatchedAnswer();
        stubAnswerLock(answer);

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), UUID.randomUUID(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);

        assertThat(changed).isFalse();
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    private boolean persist(ScoringAnswer answer, long token, AiScoreResult result) {
        return service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), token, result);
    }

    private boolean fail(ScoringAnswer answer, long token, AiFailureReason reason) {
        return service.markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), token, reason);
    }

    private void stubAnswerLock(ScoringAnswer answer) {
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
    }

    private ScoringAnswerAiDetail savedDetail() {
        ArgumentCaptor<ScoringAnswerAiDetail> captor = ArgumentCaptor.forClass(ScoringAnswerAiDetail.class);
        verify(detailRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ScoringSessionState publishedState(ScoringAnswer answer) {
        ScoringSessionState state = new ScoringSessionState(answer.getTenantId(), answer.getSessionPublicId());
        state.lockForPublication(UUID.randomUUID(), Instant.now());
        return state;
    }

    private ScoringAnswer dispatchedAnswer() {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setTaskType("READ_ALOUD");
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        answer.markAiDispatched(DISPATCHED_AT);
        return answer;
    }

    private AiScoreResult result() {
        return new AiScoreResult(72, Map.of("speaking", 70), "feedback",
                AiProviderCategory.REAL, "provider", "model", "v1");
    }
}
