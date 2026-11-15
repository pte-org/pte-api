package com.pte.scoring.internal.service.ai;

import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audioscoring.PracticeAudioScorer;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Practice speaking goes to the scorer that hears the recording itself, with the ACTIVE rubric, unless the deployment
 * chose Azure; writing never does.
 */
class PracticeAudioScorerRoutingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTIVE_VERSION = UUID.randomUUID();
    private static final RubricCatalog CATALOG = new RubricCatalogImpl();

    private final AtomicReference<String> scoredBy = new AtomicReference<>();
    private final AtomicReference<AiScoringRequest> heard = new AtomicReference<>();

    @Test
    void practiceSpeechIsHeardByTheModelWithTheActiveRubric() {
        PracticeAiEvaluation evaluation = service(AiProviderProperties.PRACTICE_SPEECH_GATEWAY)
                .score(request(ScoringConstants.TASK_TYPE_READ_ALOUD));

        assertThat(scoredBy.get()).isEqualTo("audio");
        assertThat(heard.get().rubric()).isNotNull();
        assertThat(heard.get().rubricVersionPublicId()).isEqualTo(ACTIVE_VERSION);
        assertThat(evaluation.provider()).isEqualTo("audio");
    }

    @Test
    void azureStaysAvailableForPracticeSpeech() {
        service(AiProviderProperties.PRACTICE_SPEECH_AZURE).score(request(ScoringConstants.TASK_TYPE_READ_ALOUD));

        assertThat(scoredBy.get()).isEqualTo("azure");
    }

    @Test
    void writingIsNeverSentToTheAudioScorer() {
        service(AiProviderProperties.PRACTICE_SPEECH_GATEWAY).score(request(ScoringConstants.TASK_TYPE_WRITE_ESSAY));

        assertThat(scoredBy.get()).isEqualTo("azure");
    }

    private PracticeAiScoringService service(String practiceSpeechVendor) {
        AiProviderProperties properties = new AiProviderProperties();
        properties.setPracticeSpeechVendor(practiceSpeechVendor);
        AiAnswerScorer examScorer = new AiAnswerScorer() {
            @Override
            public Set<String> taskTypes() {
                return Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringConstants.TASK_TYPE_WRITE_ESSAY);
            }

            @Override
            public AiScoreResult score(AiScoringRequest request) {
                scoredBy.set("azure");
                return result("azure");
            }
        };
        PracticeAudioScorer audioScorer = new PracticeAudioScorer() {
            @Override
            public Set<String> taskTypes() {
                return Set.of(ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringConstants.TASK_TYPE_WRITE_ESSAY);
            }

            @Override
            public AiScoreResult score(AiScoringRequest request) {
                scoredBy.set("audio");
                heard.set(request);
                return result("audio");
            }
        };
        return new PracticeAiScoringService(List.of(examScorer), List.of(audioScorer),
                (media, ref, tenant) -> result("generic"), (text, prompt) -> result("generic"),
                new ObjectiveScoringService(JsonMapper.builder().build()), CLOCK, properties, activeRubric());
    }

    private static AiScoreResult result(String provider) {
        return new AiScoreResult(80, Map.of("CONTENT", 3), "ok", AiProviderCategory.REAL, provider, "m", null,
                "words", "{}", false, ACTIVE_VERSION);
    }

    private static RubricResolver activeRubric() {
        RubricResolver resolver = mock(RubricResolver.class);
        when(resolver.resolveActiveVersion(anyString())).thenAnswer(call -> {
            String taskType = call.getArgument(0);
            return new ResolvedRubric(ACTIVE_VERSION, new EffectiveRubric(CATALOG.find(taskType).orElseThrow(),
                    CATALOG.defaultParameters(taskType)));
        });
        return resolver;
    }

    private static PracticeScoringRequest request(String taskType) {
        return new PracticeScoringRequest(taskType, UUID.randomUUID().toString(), "prompt", "reference", "correct",
                null, null, null, null, TENANT);
    }
}
