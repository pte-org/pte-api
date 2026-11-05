package com.pte.scoring.internal.messaging.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.service.ai.AiScoringResultPersistenceService;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.scorer.WriteEssayScorer;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private static final UUID PINNED_VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    /** A text task type outside the rubric catalog: the only kind the generic essay client still scores. */
    private static final String CUSTOM_TEXT_TASK = "CUSTOM_TEXT";
    /** A speaking task type outside the rubric catalog: since phase 5 the only kind the generic speech client scores. */
    private static final String CUSTOM_SPEECH_TASK = "CUSTOM_SPEECH";

    @Mock
    private AiEvaluationRepository scoringAnswerRepository;
    @Mock
    private SpeechScoringClient speechScoringClient;
    @Mock
    private EssayScoringClient essayScoringClient;
    @Mock
    private AiScoringResultPersistenceService resultPersistenceService;
    @Mock
    private AnswerContentReader contentReader;
    @Mock
    private RubricResolver rubricResolver;

    private AiScoringWorker worker;

    @BeforeEach
    void setUp() {
        worker = workerWith(List.of());
    }

    private AiScoringWorker workerWith(List<AiAnswerScorer> scorers) {
        return new AiScoringWorker(scoringAnswerRepository, speechScoringClient, essayScoringClient, scorers,
                resultPersistenceService, Clock.fixed(NOW, ZoneOffset.UTC), new AiProviderProperties(), contentReader,
                rubricResolver);
    }

    @Test
    void worker_taskTypeWithAScorer_isScoredByItWithTheRowsContentAndNoLegacyClient() {
        AiAnswerScorer scorer = org.mockito.Mockito.mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD));
        when(scorer.score(any(AiScoringRequest.class))).thenReturn(speechResult());
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        content(answer, "payload", "the passage");
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
        AiEvaluation answer = dispatched(CUSTOM_SPEECH_TASK);
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
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
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
        AiEvaluation answer = dispatched(CUSTOM_SPEECH_TASK);
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
    void worker_aiTextJobOutsideTheRubricCatalog_routesToEssayClient() {
        stubEssayClient();
        AiEvaluation answer = dispatched(CUSTOM_TEXT_TASK);
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
        AiEvaluation answer = dispatched(CUSTOM_SPEECH_TASK);
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
        AiEvaluation answer = dispatched(CUSTOM_SPEECH_TASK);
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
        AiEvaluation answer = dispatched(CUSTOM_TEXT_TASK);
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
        AiEvaluation answer = dispatched("UNKNOWN");
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> worker.onAiScoringJob(job(answer, "UNSCORED")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");

        verifyNoInteractions(speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_supersededTokenDoesNotCallTheVendor() {
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        AiScoringJob oldJob = job(answer, "AI_SPEECH");
        answer.markAiDispatched(NOW.plusSeconds(1));
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(oldJob);

        verifyNoInteractions(speechScoringClient, essayScoringClient, resultPersistenceService);
    }

    @Test
    void worker_jobWithoutTokenIsIgnored() {
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

        worker.onAiScoringJob(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), "AI_SPEECH", null));

        verifyNoInteractions(scoringAnswerRepository, speechScoringClient, essayScoringClient,
                resultPersistenceService);
    }

    @Test
    void worker_redeliveredTerminalAnswer_doesNotCallVendorAgain() {
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
        AiScoringJob job = job(answer, "AI_SPEECH");
        answer.markScored(80, java.time.Instant.now());
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job);

        assertThat(answer.getRawScore()).isEqualTo(80);
        verifyNoInteractions(resultPersistenceService, speechScoringClient, essayScoringClient);
    }

    @Test
    void worker_doesNotWriteAiScoreAfterPublicationBarrier() {
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
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
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

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
            AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);
            content(answer, "candidate-audio-reference", "passage-secret-words");
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
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_READ_ALOUD);

        worker.onDeadLettered(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), "AI_SPEECH", null));

        verifyNoInteractions(resultPersistenceService);
    }

    // ---- rubric-scored writing task types ----

    @Test
    void worker_rubricTaskType_resolvesThePinnedRubricOfTheExamDefinitionWhenTheJobRuns() {
        AiAnswerScorer scorer = mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY));
        when(scorer.score(any(AiScoringRequest.class))).thenReturn(speechResult());
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        EffectiveRubric pinned = rubric(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(rubricResolver.resolveForExamDefinition(answer.getExamDefinitionPublicId(),
                ScoringConstants.TASK_TYPE_WRITE_ESSAY)).thenReturn(new ResolvedRubric(PINNED_VERSION, pinned));

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_TEXT"));

        ArgumentCaptor<AiScoringRequest> request = ArgumentCaptor.forClass(AiScoringRequest.class);
        verify(scorer).score(request.capture());
        assertThat(request.getValue().rubric()).isSameAs(pinned);
        assertThat(request.getValue().rubricVersionPublicId()).isEqualTo(PINNED_VERSION);
        assertThat(request.getValue().toString()).doesNotContain("payload");
        verify(resultPersistenceService).persistAiScore(eq(answer.getAnswerPublicId()), eq(answer.getAttemptPublicId()),
                eq(answer.getTenantId()), eq(answer.getSessionPublicId()), eq(answer.getDispatchToken()),
                any(AiScoreResult.class));
    }

    @Test
    void worker_nonRubricTaskType_isScoredWithoutResolvingARubric() {
        AiAnswerScorer scorer = mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(CUSTOM_SPEECH_TASK));
        when(scorer.score(any(AiScoringRequest.class))).thenReturn(speechResult());
        AiEvaluation answer = dispatched(CUSTOM_SPEECH_TASK);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_SPEECH"));

        ArgumentCaptor<AiScoringRequest> request = ArgumentCaptor.forClass(AiScoringRequest.class);
        verify(scorer).score(request.capture());
        assertThat(request.getValue().rubric()).isNull();
        verifyNoInteractions(rubricResolver);
    }

    @Test
    void worker_rubricThatCannotBeResolved_failsTheAnswerWithoutCallingTheScorer() {
        AiAnswerScorer scorer = mock(AiAnswerScorer.class);
        when(scorer.taskTypes()).thenReturn(Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY));
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(rubricResolver.resolveForExamDefinition(any(), any()))
                .thenThrow(new DomainException(HttpStatus.NOT_FOUND, "RUBRIC_VERSION_NOT_FOUND") { });

        workerWith(List.of(scorer)).onAiScoringJob(job(answer, "AI_TEXT"));

        verify(scorer, never()).score(any());
        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);
    }

    /** Until phase 4 decides, a provider with no rubric scorer must not let its generic client invent a score. */
    @Test
    void worker_rubricTaskTypeWithoutARubricScorer_failsClearlyInsteadOfUsingTheGenericEssayClient() {
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));

        worker.onAiScoringJob(job(answer, "AI_TEXT"));

        verifyNoInteractions(essayScoringClient, speechScoringClient, rubricResolver);
        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);
    }

    @Test
    void worker_modelThatKeepsBreakingTheSchema_isAskedThreeTimesThenTheAnswerIsScoringFailed() {
        GeminiRestClient gemini = mock(GeminiRestClient.class);
        when(gemini.generate(any())).thenReturn(new GeminiResponse(JsonMapper.builder().build().readTree(
                "{\"CONTENT\": 7, \"DEVELOPMENT_STRUCTURE_COHERENCE\": 3.5, \"GRAMMAR\": 2, "
                        + "\"GENERAL_LINGUISTIC_RANGE\": 4, \"VOCABULARY_RANGE\": 1, \"feedback\": \"\", "
                        + "\"errors\": []}"), "gemini-test"));
        AiEvaluation answer = dispatched(ScoringConstants.TASK_TYPE_WRITE_ESSAY);
        content(answer, "word word word word word word word word word word.", "Should cities ban cars?");
        when(scoringAnswerRepository.findByAnswerPublicId(answer.getAnswerPublicId()))
                .thenReturn(Optional.of(answer));
        when(rubricResolver.resolveForExamDefinition(any(), any())).thenReturn(
                new ResolvedRubric(PINNED_VERSION, rubric(ScoringConstants.TASK_TYPE_WRITE_ESSAY)));

        workerWith(List.of(new WriteEssayScorer(gemini, JsonMapper.builder().build())))
                .onAiScoringJob(job(answer, "AI_TEXT"));

        verify(gemini, times(3)).generate(any());
        verify(resultPersistenceService).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.PROVIDER_RESPONSE_INVALID);
        verify(resultPersistenceService, never()).persistAiScore(any(), any(), any(), any(), anyLong(), any());
    }

    private static EffectiveRubric rubric(String taskType) {
        return new EffectiveRubric(CATALOG.find(taskType).orElseThrow(), CATALOG.defaultParameters(taskType));
    }

    private AiEvaluation dispatched(String taskType) {
        AiEvaluation answer = EvaluationFixtures.ai(taskType, ScoringMethod.AI_SPEECH);
        answer.markAiDispatched(NOW);
        content(answer, "payload", null);
        return answer;
    }

    /** What the attempt holds for this answer: the candidate's payload and the question's passage. */
    private void content(AiEvaluation answer, String payload, String promptText) {
        SubmittedAnswerView view = new SubmittedAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getItemPublicId(), answer.getSessionPublicId(), answer.getTenantId(),
                answer.getExamDefinitionPublicId(), answer.getTaskType(), payload, "reference", null, false,
                promptText, null, null, null, null, answer.getSection(), null, null, null, null, null);
        lenient().when(contentReader.one(answer.getAnswerPublicId(), answer.getTenantId()))
                .thenReturn(Optional.of(view));
    }

    private AiScoringJob job(AiEvaluation answer, String scoringMethod) {
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
