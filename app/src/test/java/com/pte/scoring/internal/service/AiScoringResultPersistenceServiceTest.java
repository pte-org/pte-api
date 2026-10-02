package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScoringResultPersistenceServiceTest {

    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;

    private AiScoringResultPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new AiScoringResultPersistenceService(answerRepository, sessionStateRepository);
    }

    @Test
    void persistAiScore_locksAnswerBeforePublicationStateAndStoresResult() {
        ScoringAnswer answer = answer();
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.empty());

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), result());

        assertThat(persisted).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getRawScore()).isEqualTo(72);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        InOrder lockOrder = inOrder(answerRepository, sessionStateRepository);
        lockOrder.verify(answerRepository).findByAnswerPublicIdForUpdate(answer.getAnswerPublicId());
        lockOrder.verify(sessionStateRepository).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository).save(answer);
    }

    @Test
    void persistAiScore_doesNotWriteWhenPublicationHasLockedSession() {
        ScoringAnswer answer = answer();
        ScoringSessionState state = new ScoringSessionState(answer.getTenantId(), answer.getSessionPublicId());
        state.lockForPublication(UUID.randomUUID(), java.time.Instant.now());
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.of(state));

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), result());

        assertThat(persisted).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void persistAiScore_doesNotOverwriteAnAlreadyTerminalAnswer() {
        ScoringAnswer answer = answer();
        answer.markScored(30);
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), result());

        assertThat(persisted).isFalse();
        assertThat(answer.getRawScore()).isEqualTo(30);
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_doesNotChangeTerminalScore() {
        ScoringAnswer answer = answer();
        answer.markScored(88);
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId());

        assertThat(changed).isFalse();
        assertThat(answer.getRawScore()).isEqualTo(88);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_changesPendingAnswerWhenSessionIsNotPublished() {
        ScoringAnswer answer = answer();
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.empty());

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId());

        assertThat(changed).isTrue();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORING_FAILED);
        verify(answerRepository).save(answer);
    }

    @Test
    void markScoringFailed_doesNotChangePendingAnswerAfterPublication() {
        ScoringAnswer answer = answer();
        ScoringSessionState state = new ScoringSessionState(answer.getTenantId(), answer.getSessionPublicId());
        state.lockForPublication(UUID.randomUUID(), java.time.Instant.now());
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(sessionStateRepository.findForUpdate(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(Optional.of(state));

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId());

        assertThat(changed).isFalse();
        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void persistAiScore_ignoresAnswerFromAnotherTenantOrSession() {
        ScoringAnswer answer = answer();
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        boolean persisted = service.persistAiScore(answer.getAnswerPublicId(), answer.getAttemptPublicId(), UUID.randomUUID(),
                answer.getSessionPublicId(), result());

        assertThat(persisted).isFalse();
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    @Test
    void markScoringFailed_ignoresAnswerWhenDeadLetterAttemptDoesNotMatch() {
        ScoringAnswer answer = answer();
        when(answerRepository.findByAnswerPublicIdForUpdate(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        boolean changed = service.markScoringFailed(answer.getAnswerPublicId(), UUID.randomUUID(),
                answer.getTenantId(), answer.getSessionPublicId());

        assertThat(changed).isFalse();
        verify(sessionStateRepository, never()).findForUpdate(answer.getTenantId(), answer.getSessionPublicId());
        verify(answerRepository, never()).save(answer);
    }

    private ScoringAnswer answer() {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setTaskType("READ_ALOUD");
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        return answer;
    }

    private AiScoreResult result() {
        return new AiScoreResult(72, Map.of("speaking", 70), "feedback",
                AiProviderCategory.REAL, "provider", "model", "v1");
    }
}
