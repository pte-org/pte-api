package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Since Phase 4 (plans/score-template-exam-generation): {@code
 * AiScoringDispatcher} embeds the already-resolved {@code scoringMethod}
 * ("AI_SPEECH"/"AI_TEXT") into the job at dispatch time — {@code
 * callVendor} switches on that field, no more hardcoded-catalog lookup by
 * task type.
 */
@ExtendWith(MockitoExtension.class)
class AiScoringWorkerTest {

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private SpeechScoringClient speechScoringClient;
    @Mock
    private EssayScoringClient essayScoringClient;

    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        worker = new AiScoringWorker(scoringAnswerRepository, speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_aiSpeechJob_routesToSpeechClient() {
        stubSpeechClient();
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.STUB);
        assertThat(answer.getAiProvider()).isEqualTo("STUB");
        verify(speechScoringClient).score(answer.getPayload(), answer.getCorrectAnswerText(), answer.getTenantId());
        verifyNoInteractions(essayScoringClient);
    }

    @Test
    void worker_aiTextJob_routesToEssayClient() {
        stubEssayClient();
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_TEXT"));

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(answer.getAiProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(answer.getAiModel()).isEqualTo("test-model");
        verify(essayScoringClient).score(answer.getPayload(), answer.getCorrectAnswerText());
        verifyNoInteractions(speechScoringClient);
    }

    @Test
    void worker_rejectsUnknownScoringMethodWithoutCallingEitherClient() {
        ScoringAnswer answer = answer("UNKNOWN");
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> worker.onAiScoringJob(job(answer, "UNSCORED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verifyNoInteractions(speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_redeliveredTerminalAnswer_doesNotCallVendorAgain() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        answer.markScored(80);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        assertThat(answer.getRawScore()).isEqualTo(80);
        assertThat(answer.getAiProviderCategory()).isNull();
        verify(speechScoringClient, never()).score(anyString(), anyString(), any(UUID.class));
        verifyNoInteractions(essayScoringClient);
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setTaskType(taskType);
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        return answer;
    }

    private AiScoringJob job(ScoringAnswer answer, String scoringMethod) {
        return new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), answer.getTaskType(),
                answer.getPayload(), answer.getCorrectAnswerText(), scoringMethod);
    }

    private void stubSpeechClient() {
        when(speechScoringClient.score(anyString(), anyString(), any(UUID.class)))
                .thenReturn(new AiScoreResult(65, Map.of(), "speech stub",
                        AiProviderCategory.STUB, "STUB", null, null));
    }

    private void stubEssayClient() {
        when(essayScoringClient.score(anyString(), anyString()))
                .thenReturn(new AiScoreResult(60, Map.of(), "text result",
                        AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "test-model", null));
    }
}
