package com.pte.scoring.internal.messaging.consumer;

import com.pte.scoring.domain.ScoringAnswer;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScoringWorkerTest {

    private static final List<String> SPEECH_TYPES = List.of(
            ScoringConstants.TASK_TYPE_READ_ALOUD,
            ScoringConstants.TASK_TYPE_REPEAT_SENTENCE,
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE,
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE,
            ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION,
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION);

    private static final List<String> TEXT_TYPES = List.of(
            ScoringConstants.TASK_TYPE_WRITE_ESSAY,
            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT,
            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT);

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
    void worker_routesEverySpeechTaskToSpeechClient() {
        stubSpeechClient();
        for (String taskType : SPEECH_TYPES) {
            ScoringAnswer answer = answer(taskType);
            when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                    .thenReturn(Optional.of(answer));

            worker.onAiScoringJob(job(answer));

            assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
            verify(speechScoringClient).score(answer.getPayload(), answer.getCorrectAnswerText(), answer.getTenantId());
            verifyNoInteractions(essayScoringClient);
            clearInvocations(speechScoringClient, essayScoringClient);
        }
    }

    @Test
    void worker_routesEveryTextTaskToEssayClient() {
        stubEssayClient();
        for (String taskType : TEXT_TYPES) {
            ScoringAnswer answer = answer(taskType);
            when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                    .thenReturn(Optional.of(answer));

            worker.onAiScoringJob(job(answer));

            assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
            verify(essayScoringClient).score(answer.getPayload(), answer.getCorrectAnswerText());
            verifyNoInteractions(speechScoringClient);
            clearInvocations(speechScoringClient, essayScoringClient);
        }
    }

    @Test
    void worker_rejectsUnsupportedJobWithoutCallingEitherClient() {
        ScoringAnswer answer = answer("UNKNOWN");
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> worker.onAiScoringJob(job(answer)))
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

        worker.onAiScoringJob(job(answer));

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

    private AiScoringJob job(ScoringAnswer answer) {
        return new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), answer.getTaskType(),
                answer.getPayload(), answer.getCorrectAnswerText());
    }

    private void stubSpeechClient() {
        when(speechScoringClient.score(anyString(), anyString(), any(UUID.class)))
                .thenReturn(new AiScoreResult(65, Map.of(), "speech stub"));
    }

    private void stubEssayClient() {
        when(essayScoringClient.score(anyString(), anyString()))
                .thenReturn(new AiScoreResult(60, Map.of(), "text stub"));
    }
}
