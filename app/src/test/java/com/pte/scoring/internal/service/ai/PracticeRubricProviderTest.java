package com.pte.scoring.internal.service.ai;

import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.support.NonRubricProviderClients;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Decision B of the rubric plan (phase 4) on the practice path: with the real {@code stub} or
 * {@code openai-compatible} clients and no rubric scorer, a rubric task type fails as {@code INTERNAL} and the
 * LLM-supplied {@code rawScore} of the generic client never becomes a practice result.
 */
class PracticeRubricProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneOffset.UTC);

    static Stream<Arguments> providerAndTaskType() {
        return NonRubricProviderClients.all().stream().flatMap(clients -> ScoringConstants.RUBRIC_SCORED_TASK_TYPES
                .stream().sorted().map(taskType -> Arguments.of(clients, taskType)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("providerAndTaskType")
    void rubricTaskTypeFailsAndIsNeverScoredByTheProvidersGenericClient(NonRubricProviderClients clients,
                                                                      String taskType) {
        RubricResolver rubricResolver = mock(RubricResolver.class);
        PracticeAiScoringService service = new PracticeAiScoringService(List.of(), List.of(), clients.speech(), clients.essay(),
                new ObjectiveScoringService(JsonMapper.builder().build()), CLOCK, new AiProviderProperties(),
                rubricResolver);

        assertThatThrownBy(() -> service.score(new PracticeScoringRequest(taskType,
                "Cities are warmer than the countryside because hard surfaces store heat.", "the source passage",
                "the source passage", null, null, null, null, null, UUID.randomUUID())))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INTERNAL));
        clients.verifyNeverScored();
        verifyNoInteractions(rubricResolver);
    }
}
