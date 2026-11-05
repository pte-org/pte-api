package com.pte.scoring.internal.messaging.consumer;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.service.ai.AiScoringResultPersistenceService;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.support.NonRubricProviderClients;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Decision B of the rubric plan (phase 4): {@code stub} and {@code openai-compatible} register no rubric scorer, so
 * for every rubric task type, whatever {@code scoringMethod} the job carries, the exam worker fails the answer
 * ({@code AI_RUBRIC_SCORER_UNAVAILABLE} -> {@code INTERNAL_ERROR}) and never lets their generic client supply a
 * {@code rawScore}.
 */
class AiScoringWorkerRubricProviderTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private final AiEvaluationRepository repository = mock(AiEvaluationRepository.class);
    private final AiScoringResultPersistenceService persistence = mock(AiScoringResultPersistenceService.class);
    private final AnswerContentReader contentReader = mock(AnswerContentReader.class);
    private final RubricResolver rubricResolver = mock(RubricResolver.class);

    static Stream<Arguments> providerTaskTypeAndMethod() {
        return NonRubricProviderClients.all().stream().flatMap(clients -> ScoringConstants.RUBRIC_SCORED_TASK_TYPES
                .stream().sorted().flatMap(taskType -> Stream.of("AI_TEXT", "AI_SPEECH")
                        .map(method -> Arguments.of(clients, taskType, method))));
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("providerTaskTypeAndMethod")
    void rubricTaskTypeIsFailedAndNeverScoredByTheProvidersGenericClient(NonRubricProviderClients clients,
                                                                       String taskType, String scoringMethod) {
        AiEvaluation answer = EvaluationFixtures.ai(taskType, ScoringMethod.AI_TEXT);
        answer.markAiDispatched(NOW);
        when(repository.findByAnswerPublicId(answer.getAnswerPublicId())).thenReturn(Optional.of(answer));
        when(contentReader.one(answer.getAnswerPublicId(), answer.getTenantId())).thenReturn(Optional.of(view(answer)));
        AiScoringWorker worker = new AiScoringWorker(repository, clients.speech(), clients.essay(), List.of(),
                persistence, Clock.fixed(NOW, ZoneOffset.UTC), new AiProviderProperties(), contentReader,
                rubricResolver);

        worker.onAiScoringJob(new AiScoringJob(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId(), scoringMethod, answer.getDispatchToken()));

        clients.verifyNeverScored();
        verifyNoInteractions(rubricResolver);
        verify(persistence).markScoringFailed(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getTenantId(), answer.getSessionPublicId(), answer.getDispatchToken(),
                AiFailureReason.INTERNAL_ERROR);
        verify(persistence, never()).persistAiScore(any(), any(), any(), any(), anyLong(), any());
    }

    /** A well-formed answer, so nothing but the missing rubric scorer can stop it. */
    private static SubmittedAnswerView view(AiEvaluation answer) {
        return new SubmittedAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getItemPublicId(), answer.getSessionPublicId(), answer.getTenantId(),
                answer.getExamDefinitionPublicId(), answer.getTaskType(),
                "Cities are warmer than the countryside because hard surfaces store heat.", "reference", null, false,
                "the source passage", null, null, null, null, answer.getSection(), null, null, null, null, null);
    }
}
