package com.pte.scoring.internal.messaging.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.service.AiScoringResultPersistenceService;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The job carries only identifiers, the resolved {@code scoringMethod} and the
 * dispatch token; the worker reads the candidate's answer from the scoring row
 * and only acts while that row still awaits this token.
 */
@ExtendWith(MockitoExtension.class)
class AiScoringWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private SpeechScoringClient speechScoringClient;
    @Mock
    private EssayScoringClient essayScoringClient;
    @Mock
    private AiScoringResultPersistenceService resultPersistenceService;

    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        worker = workerWith(List.of());
    }

    private AiScoringWorker workerWith(List<AiAnswerScorer> scorers) {
        return new AiScoringWorker(scoringAnswerRepository, speechScoringClient, essayScoringClient, scorers,
                resultPersistenceService, Clock.fixed(NOW, ZoneOffset.UTC), new AiProviderProperties());
    }

    @Test
    void worker_taskTypeWithAScorer_isScoredByItWithTheRowsContentAndNoLegacyClient() {
        AiAnswerScorer scorer = org.mockito.Mockito.mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
        when(scorer.score(any(AiScoringRequest.class))).thenReturn(speechResult());
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        answer.applyQuestionContent("the passage", null, null, null, null);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_SPEECH"));

        ArgumentCaptor<AiScoringRequest> request = ArgumentCaptor.forClass(AiScoringRequest.class);
        verify(scorer).score(request.capture());
        assertThat(request.getValue().taskType()).isEqualTo(ScoringConstants.TASK_TYPE_READ_ALOUD);
        assertThat(request.getValue().payload()).isEqualTo("payload");
        assertThat(request.getValue().promptText()).isEqualTo("the passage");
        assertThat(request.getValue().correctAnswerText()).isEqualTo("reference");
        assertThat(request.getValue().tenantId()).isEqualTo(answer.getTenantId());
        verify(resultPersistenceService).persistAiScore(eq(answer.getAnswerPublicId()), eq(answer.getAttemptPublicId()),
                eq(answer.getTenantId()), eq(answer.getSessionPublicId()), eq(answer.getDispatchToken()),
                any(AiScoreResult.class));
        verifyNoInteractions(speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_taskTypeWithoutAScorer_fallsBackToTheGenericClient() {
        AiAnswerScorer scorer = org.mockito.Mockito.mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
        stubSpeechClient();
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_SPEECH"));

        verify(speechScoringClient).score("payload", "reference", answer.getTenantId());
        verify(scorer, never()).score(any());
    }

    @Test
    void worker_scorerFailureMarksTheAnswerFailedWithItsReason() {
        AiAnswerScorer scorer = org.mockito.Mockito.mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
        when(scorer.score(any())).thenThrow(new AiProviderException(AiFailureReason.AUDIO_UNSUPPORTED, "bad audio"));
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_SPEECH"));

        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.AUDIO_UNSUPPORTED);
    }

    @Test
    void worker_twoScorersForOneTaskTypeIsAConfigurationError() {
        AiAnswerScorer first = org.mockito.Mockito.mock(AiAnswerScorer.class);
        AiAnswerScorer second = org.mockito.Mockito.mock(AiAnswerScorer.class);
        when(first.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
        when(second.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));

        assertThatThrownBy(() -> workerWith(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ScoringConstants.TASK_TYPE_READ_ALOUD);
    }

    @Test
    void worker_aiSpeechJob_readsTheAnswerFromTheRowAndRoutesToSpeechClient() {
        stubSpeechClient();
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        verify(speechScoringClient).score("payload", "reference", answer.getTenantId());
        verify(resultPersistenceService).persistAiScore(eq(answer.getAnswerPublicId()), eq(answer.getAttemptPublicId()),
                eq(answer.getTenantId()), eq(answer.getSessionPublicId()), eq(answer.getDispatchToken()),
                any(AiScoreResult.class));
        verifyNoInteractions(essayScoringClient);
    }

    @Test
    void worker_aiTextJob_routesToEssayClient() {
        stubEssayClient();
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_TEXT"));

        verify(essayScoringClient).score("payload", "reference");
        verify(resultPersistenceService).persistAiScore(eq(answer.getAnswerPublicId()), eq(answer.getAttemptPublicId()),
                eq(answer.getTenantId()), eq(answer.getSessionPublicId()), eq(answer.getDispatchToken()),
                any(AiScoreResult.class));
        verifyNoInteractions(speechScoringClient);
    }

    @Test
    void worker_vendorCallRunsInsideTheJobBudget() {
        AtomicReference<Optional<Duration>> remainingDuringCall = new AtomicReference<>();
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(speechScoringClient.score(anyString(), anyString(), any(UUID.class))).thenAnswer(invocation -> {
            remainingDuringCall.set(AiJobBudget.remaining());
            return speechResult();
        });

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        assertThat(remainingDuringCall.get())
                .contains(Duration.ofMillis(new AiProviderProperties().getJobBudgetMs()));
        assertThat(AiJobBudget.remaining()).isEmpty();
    }

    @Test
    void worker_vendorFailureMarksTheAnswerFailedWithItsReason() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(speechScoringClient.score(anyString(), anyString(), any(UUID.class)))
                .thenThrow(new AiProviderException(AiFailureReason.PROVIDER_AUTH_FAILED, "AZURE_SPEECH HTTP 401"));

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.PROVIDER_AUTH_FAILED);
        verify(resultPersistenceService, never()).persistAiScore(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void worker_vendorFailureWithoutReasonIsRecordedAsInternalError() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(essayScoringClient.score(anyString(), anyString()))
                .thenThrow(new AiProviderException("legacy adapter failure"));

        worker.onAiScoringJob(job(answer, "AI_TEXT"));

        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);
    }

    @Test
    void worker_rejectsUnknownScoringMethodWithoutCallingEitherClient() {
        ScoringAnswer answer = dispatched("UNKNOWN");
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> worker.onAiScoringJob(job(answer, "UNSCORED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");

        verifyNoInteractions(speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_supersededTokenDoesNotCallTheVendor() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        AiScoringJob oldJob = job(answer, "AI_SPEECH");
        answer.markAiDispatched(NOW.plusSeconds(1));
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(oldJob);

        verifyNoInteractions(speechScoringClient, essayScoringClient, resultPersistenceService);
    }

    @Test
    void worker_jobWithoutTokenIsIgnored() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

        worker.onAiScoringJob(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), "AI_SPEECH", null));

        verifyNoInteractions(scoringAnswerRepository, speechScoringClient, essayScoringClient,
                resultPersistenceService);
    }

    @Test
    void worker_redeliveredTerminalAnswer_doesNotCallVendorAgain() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        AiScoringJob job = job(answer, "AI_SPEECH");
        answer.markScored(80);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job);

        assertThat(answer.getRawScore()).isEqualTo(80);
        verifyNoInteractions(resultPersistenceService, speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_doesNotWriteAiScoreAfterPublicationBarrier() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(resultPersistenceService.isPublicationLocked(answer.getTenantId(), answer.getSessionPublicId()))
                .thenReturn(true);

        worker.onAiScoringJob(job(answer, "AI_SPEECH"));

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verifyNoInteractions(speechScoringClient);
        verify(resultPersistenceService, never()).persistAiScore(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    void worker_deadLetterMarksTheDispatchFailedAsInternalError() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

        worker.onDeadLettered(job(answer, "AI_SPEECH"));

        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);
    }

    @Test
    void worker_vendorFailureLogsOnlyTheAnswerIdAndReasonNeverTheContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(AiScoringWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AiAnswerScorer scorer = org.mockito.Mockito.mock(AiAnswerScorer.class);
            when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
            when(scorer.score(any())).thenThrow(new AiProviderException(AiFailureReason.AUDIO_UNSUPPORTED,
                    "transcript-secret-words"));
            ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
            answer.setPayload("candidate-audio-reference");
            answer.applyQuestionContent("passage-secret-words", null, null, null, null);
            when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                    .thenReturn(Optional.of(answer));

            workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_SPEECH"));

            assertThat(appender.list).isNotEmpty().allSatisfy(event -> {
                String line = event.getFormattedMessage();
                assertThat(line).contains(answer.getAnswerPublicId().toString())
                        .contains(AiFailureReason.AUDIO_UNSUPPORTED.name())
                        .doesNotContain("secret-words")
                        .doesNotContain("candidate-audio-reference");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void worker_deadLetterWithoutTokenIsIgnored() {
        ScoringAnswer answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

        worker.onDeadLettered(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), "AI_SPEECH", null));

        verifyNoInteractions(resultPersistenceService);
    }

    private ScoringAnswer dispatched(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setTaskType(taskType);
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        answer.markAiDispatched(NOW);
        return answer;
    }

    private AiScoringJob job(ScoringAnswer answer, String scoringMethod) {
        return new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), scoringMethod, answer.getDispatchToken());
    }

    private void stubSpeechClient() {
        when(speechScoringClient.score(anyString(), anyString(), any(UUID.class))).thenReturn(speechResult());
    }

    private static AiScoreResult speechResult() {
        return new AiScoreResult(65, Map.of(), "speech stub", AiProviderCategory.STUB, "STUB", null, null);
    }

    private void stubEssayClient() {
        when(essayScoringClient.score(anyString(), anyString()))
                .thenReturn(new AiScoreResult(60, Map.of(), "text result",
                        AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "test-model", null));
    }
}
