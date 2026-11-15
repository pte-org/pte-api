package com.pte.scoring.internal.service.ai;

import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import com.pte.scoring.PracticeScoringRoute;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.service.ResolvedRubric;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The practice facade must route every one of the 23 task types exactly like
 * the exam, pass the same authored content to the scorers, and turn failures
 * into a public reason instead of an empty result.
 */
class PracticeAiScoringServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final String MC_SINGLE_OPTIONS =
            "[{\"text\":\"London\",\"correct\":false,\"orderIndex\":0},"
                    + "{\"text\":\"Paris\",\"correct\":true,\"orderIndex\":1}]";

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private static final UUID ACTIVE_VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000f6");

    private final AtomicReference<AiScoringRequest> seen = new AtomicReference<>();
    private final RubricResolver rubricResolver = activeRubricResolver();
    private final AtomicReference<Duration> budgetSeen = new AtomicReference<>();

    private static AiScoreResult result(int score, boolean fallback) {
        return new AiScoreResult(score, Map.of("content", score), "ok", AiProviderCategory.REAL, "test-provider",
                "test-model", "v1", "spoken words", "{\"k\":1}", fallback);
    }

    private AiAnswerScorer scorerFor(Set<String> taskTypes, Function<AiScoringRequest, AiScoreResult> body) {
        return new AiAnswerScorer() {
            @Override
            public Set<String> taskTypes() {
                return taskTypes;
            }

            @Override
            public AiScoreResult score(AiScoringRequest request) {
                seen.set(request);
                AiJobBudget.remaining().ifPresent(budgetSeen::set);
                return body.apply(request);
            }
        };
    }

    private PracticeAiScoringService service(List<AiAnswerScorer> scorers, SpeechScoringClient speech,
            EssayScoringClient essay) {
        return new PracticeAiScoringService(scorers, List.of(), speech, essay,
                new ObjectiveScoringService(JsonMapper.builder().build()), CLOCK, new AiProviderProperties(),
                rubricResolver);
    }

    /** Practice has no exam definition: it always scores with the ACTIVE rubric version. */
    private static RubricResolver activeRubricResolver() {
        RubricResolver resolver = mock(RubricResolver.class);
        when(resolver.resolveActiveVersion(anyString())).thenAnswer(call -> {
            String taskType = call.getArgument(0);
            return new ResolvedRubric(ACTIVE_VERSION, new EffectiveRubric(CATALOG.find(taskType).orElseThrow(),
                    CATALOG.defaultParameters(taskType)));
        });
        return resolver;
    }

    private PracticeAiScoringService serviceWith(AiAnswerScorer scorer) {
        return service(List.of(scorer), (media, ref, tenant) -> {
            throw new AssertionError("generic speech client must not run");
        }, (text, prompt) -> {
            throw new AssertionError("generic essay client must not run");
        });
    }

    private static PracticeScoringRequest request(String taskType, String payload) {
        return new PracticeScoringRequest(taskType, payload, "prompt", "reference", "correct",
                UUID.fromString("00000000-0000-0000-0000-000000000042"), 5, 75, null, TENANT);
    }

    // ---- routing parity over all 23 task types ----

    @Test
    void route_everyTaskTypeMapsToTheExamProfileRoute() {
        PracticeAiScoringService service = service(List.of(), (m, r, t) -> result(1, false),
                (t, p) -> result(1, false));
        Map<PracticeScoringRoute, Integer> counts = new EnumMap<>(PracticeScoringRoute.class);
        for (PteTaskType taskType : PteTaskType.values()) {
            PracticeScoringRoute route = service.routeFor(taskType.name());
            assertThat(route).as(taskType.name()).isNotEqualTo(PracticeScoringRoute.UNSUPPORTED);
            counts.merge(route, 1, Integer::sum);
        }
        assertThat(PteTaskType.values()).hasSize(23);
        assertThat(counts).containsEntry(PracticeScoringRoute.UNSCORED, 1)
                .containsEntry(PracticeScoringRoute.AI_SPEECH, 7)
                .containsEntry(PracticeScoringRoute.AI_TEXT, 3)
                .containsEntry(PracticeScoringRoute.OBJECTIVE, 12);
        assertThat(service.routeFor("PERSONAL_INTRODUCTION")).isEqualTo(PracticeScoringRoute.UNSCORED);
        assertThat(service.routeFor("WRITE_EMAIL")).isEqualTo(PracticeScoringRoute.UNSUPPORTED);
        assertThat(service.routeFor(null)).isEqualTo(PracticeScoringRoute.UNSUPPORTED);
    }

    // ---- AI route passes the full authored content, inside a job budget ----

    @Test
    void aiSpeech_passesEveryAuthoredFieldToTheScorer() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("DESCRIBE_IMAGE"), r -> result(72, false)));

        PracticeAiEvaluation evaluation = service.score(request("describe_image", "media-1"));

        AiScoringRequest passed = seen.get();
        assertThat(passed.taskType()).isEqualTo("DESCRIBE_IMAGE");
        assertThat(passed.payload()).isEqualTo("media-1");
        assertThat(passed.promptText()).isEqualTo("prompt");
        assertThat(passed.referenceAnswerText()).isEqualTo("reference");
        assertThat(passed.correctAnswerText()).isEqualTo("correct");
        assertThat(passed.imagePromptRef()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000042"));
        assertThat(passed.minWordCount()).isEqualTo(5);
        assertThat(passed.maxWordCount()).isEqualTo(75);
        assertThat(passed.tenantId()).isEqualTo(TENANT);
        assertThat(evaluation.rawScore()).isEqualTo(72);
        assertThat(evaluation.scoreStatus()).isEqualTo("UNVALIDATED");
        assertThat(evaluation.provider()).isEqualTo("test-provider");
        assertThat(evaluation.feedback()).isEqualTo("ok");
        assertThat(evaluation.resultHash()).hasSize(64);
        assertThat(evaluation.evaluatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void aiText_runsThroughTheTextScorer() {
        PracticeAiScoringService service = serviceWith(
                scorerFor(Set.of("SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY"), r -> result(64, false)));

        PracticeAiEvaluation evaluation = service.score(request("WRITE_ESSAY", "An essay body."));

        assertThat(seen.get().payload()).isEqualTo("An essay body.");
        assertThat(seen.get().rubricVersionPublicId()).isEqualTo(ACTIVE_VERSION);
        assertThat(seen.get().rubric().task().taskType()).isEqualTo("WRITE_ESSAY");
        assertThat(evaluation.rawScore()).isEqualTo(64);
    }

    @Test
    void aiText_carriesTheRubricVersionOfTheResultIntoTheEvaluation() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("WRITE_ESSAY"), r -> new AiScoreResult(65,
                Map.of("CONTENT", 4, "FORM", 1), "ok", AiProviderCategory.REAL, "GEMINI", "m", null, null, "{}",
                false, r.rubricVersionPublicId())));

        PracticeAiEvaluation evaluation = service.score(request("WRITE_ESSAY", "An essay body."));

        assertThat(evaluation.rubricVersionPublicId()).isEqualTo(ACTIVE_VERSION);
        assertThat(evaluation.subScores()).containsEntry("CONTENT", 4).containsEntry("FORM", 1);
    }

    @Test
    void aiText_withNoActiveRubricFailsInternallyWithoutCallingTheScorer() {
        when(rubricResolver.resolveActiveVersion("SUMMARIZE_SPOKEN_TEXT")).thenThrow(
                new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_VERSION_NO_ACTIVE));
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("SUMMARIZE_SPOKEN_TEXT"), r -> {
            throw new AssertionError("scorer must not run without a rubric");
        }));

        assertThatThrownBy(() -> service.score(request("SUMMARIZE_SPOKEN_TEXT", "A summary.")))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INTERNAL));
    }

    @Test
    void speechTypes_areScoredWithTheActiveRubricSincePhaseFive() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(50, false)));

        service.score(request("READ_ALOUD", "media-1"));

        assertThat(seen.get().rubric().task().taskType()).isEqualTo("READ_ALOUD");
        assertThat(seen.get().rubricVersionPublicId()).isEqualTo(ACTIVE_VERSION);
    }

    @Test
    void aiRoute_runsInsideAnAiJobBudget() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(50, false)));

        service.score(request("READ_ALOUD", "media-1"));

        assertThat(budgetSeen.get()).isNotNull();
        assertThat(AiJobBudget.remaining()).isEmpty();
    }

    @Test
    void fallbackResult_isMarkedFallback() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(40, true)));

        assertThat(service.score(request("READ_ALOUD", "media-1")).scoreStatus()).isEqualTo("FALLBACK");
    }

    /** Phase 5: every scored speaking task type is rubric-scored too, so the generic speech client never scores it. */
    @Test
    void rubricSpeechTypeWithoutScorer_failsInsteadOfFallingBackToTheGenericSpeechClient() {
        for (String taskType : List.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE",
                "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION", "ANSWER_SHORT_QUESTION")) {
            PracticeAiScoringService service = service(List.of(), (media, ref, tenant) -> {
                throw new AssertionError("generic speech client must not score a rubric task type");
            }, (text, prompt) -> {
                throw new AssertionError("essay client must not run for speech");
            });

            assertThatThrownBy(() -> service.score(request(taskType, "media-9")))
                    .isInstanceOfSatisfying(PracticeScoringException.class,
                            ex -> assertThat(ex.failure()).as(taskType).isEqualTo(PracticeScoringFailure.INTERNAL));
        }
    }

    /**
     * The three writing task types are rubric-scored; the generic essay client of another provider must not invent
     * a 0-100 score for them (phase 4 decides that provider), so practice fails them clearly instead.
     */
    @Test
    void rubricTextTypeWithoutScorer_failsInsteadOfFallingBackToTheGenericEssayClient() {
        for (String taskType : List.of("WRITE_ESSAY", "SUMMARIZE_WRITTEN_TEXT", "SUMMARIZE_SPOKEN_TEXT")) {
            PracticeAiScoringService service = service(List.of(), (m, r, t) -> {
                throw new AssertionError("speech client must not run for text");
            }, (text, prompt) -> {
                throw new AssertionError("generic essay client must not score a rubric task type");
            });

            assertThatThrownBy(() -> service.score(request(taskType, "my text")))
                    .isInstanceOfSatisfying(PracticeScoringException.class,
                            ex -> assertThat(ex.failure()).as(taskType).isEqualTo(PracticeScoringFailure.INTERNAL));
        }
        verifyNoInteractions(rubricResolver);
    }

    // ---- failures become public reasons, never an empty result ----

    @Test
    void providerFailures_mapToPublicReasons() {
        Map<AiFailureReason, PracticeScoringFailure> expected = Map.ofEntries(
                Map.entry(AiFailureReason.PROVIDER_RATE_LIMITED, PracticeScoringFailure.PROVIDER_UNAVAILABLE),
                Map.entry(AiFailureReason.PROVIDER_UNAVAILABLE, PracticeScoringFailure.PROVIDER_UNAVAILABLE),
                Map.entry(AiFailureReason.PROVIDER_NETWORK_ERROR, PracticeScoringFailure.PROVIDER_UNAVAILABLE),
                Map.entry(AiFailureReason.PROVIDER_TIMEOUT, PracticeScoringFailure.BUDGET_EXCEEDED),
                Map.entry(AiFailureReason.PROVIDER_AUTH_FAILED, PracticeScoringFailure.PROVIDER_REJECTED),
                Map.entry(AiFailureReason.PROVIDER_BAD_REQUEST, PracticeScoringFailure.PROVIDER_REJECTED),
                Map.entry(AiFailureReason.PROVIDER_RESPONSE_INVALID, PracticeScoringFailure.PROVIDER_REJECTED),
                Map.entry(AiFailureReason.PROVIDER_CONTENT_BLOCKED, PracticeScoringFailure.PROVIDER_REJECTED),
                Map.entry(AiFailureReason.AUDIO_UNSUPPORTED, PracticeScoringFailure.INVALID_PAYLOAD),
                Map.entry(AiFailureReason.AUDIO_TOO_LONG, PracticeScoringFailure.INVALID_PAYLOAD),
                Map.entry(AiFailureReason.INTERNAL_ERROR, PracticeScoringFailure.INTERNAL));
        assertThat(expected.keySet()).containsExactlyInAnyOrder(AiFailureReason.values());
        expected.forEach((reason, failure) -> {
            PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> {
                throw new AiProviderException(reason, "boom");
            }));
            assertThatThrownBy(() -> service.score(request("READ_ALOUD", "media-1")))
                    .isInstanceOfSatisfying(PracticeScoringException.class,
                            ex -> assertThat(ex.failure()).as(reason.name()).isEqualTo(failure));
        });
    }

    @Test
    void unclassifiedScorerException_isInternalAndKeepsTheAnswerOutOfTheMessage() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("WRITE_ESSAY"), r -> {
            throw new IllegalStateException("secret essay text");
        }));

        assertThatThrownBy(() -> service.score(request("WRITE_ESSAY", "secret essay text")))
                .isInstanceOfSatisfying(PracticeScoringException.class, ex -> {
                    assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INTERNAL);
                    assertThat(ex.getMessage()).doesNotContain("secret");
                });
    }

    @Test
    void blankAiPayload_isInvalidAndNeverReachesTheVendor() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> {
            throw new AssertionError("vendor must not run");
        }));

        assertThatThrownBy(() -> service.score(request("READ_ALOUD", "  ")))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INVALID_PAYLOAD));
    }

    @Test
    void unscoredAndUnknownTypes_areUnsupported() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(1, false)));

        for (String taskType : List.of("PERSONAL_INTRODUCTION", "WRITE_EMAIL", "")) {
            assertThatThrownBy(() -> service.score(request(taskType, "media-1")))
                    .isInstanceOfSatisfying(PracticeScoringException.class,
                            ex -> assertThat(ex.failure()).as(taskType)
                                    .isEqualTo(PracticeScoringFailure.UNSUPPORTED_TASK_TYPE));
        }
    }

    // ---- objective route matches exam scoring ----

    @Test
    void objective_matchesExamObjectiveScoringForTheSameInput() {
        PracticeAiScoringService service = service(List.of(), (m, r, t) -> result(1, false),
                (t, p) -> result(1, false));
        ObjectiveScoringService exam = new ObjectiveScoringService(JsonMapper.builder().build());

        for (String payload : List.of("0", "1")) {
            var answer = com.pte.scoring.support.SubmittedAnswerFixtures.answer("MC_READING_SINGLE",
                    MC_SINGLE_OPTIONS, payload);
            PracticeAiEvaluation evaluation = service.score(new PracticeScoringRequest("MC_READING_SINGLE", payload,
                    null, null, null, null, null, null, MC_SINGLE_OPTIONS, TENANT));
            assertThat(evaluation.rawScore()).isEqualTo(exam.score(answer));
            assertThat(evaluation.scoreStatus()).isEqualTo("UNVALIDATED");
            assertThat(evaluation.provider()).isEqualTo("objective-scoring");
            assertThat(evaluation.resultHash()).hasSize(64);
        }
        assertThat(seen.get()).isNull();
    }

    // ---- result hash, null request, logging safety, speech adapter ----

    @Test
    void resultHash_isStableForSameInputAndChangesWithAnswerOrScore() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(50, false)));
        PracticeAiScoringService other = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(51, false)));

        String hash = service.score(request("READ_ALOUD", "media-1")).resultHash();

        assertThat(service.score(request("READ_ALOUD", "media-1")).resultHash()).isEqualTo(hash);
        assertThat(service.score(request("READ_ALOUD", "media-2")).resultHash()).isNotEqualTo(hash);
        assertThat(other.score(request("READ_ALOUD", "media-1")).resultHash()).isNotEqualTo(hash);
    }

    @Test
    void nullRequest_isInvalidPayload() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("READ_ALOUD"), r -> result(1, false)));

        assertThatThrownBy(() -> service.score(null))
                .isInstanceOfSatisfying(PracticeScoringException.class,
                        ex -> assertThat(ex.failure()).isEqualTo(PracticeScoringFailure.INVALID_PAYLOAD));
    }

    @Test
    void toStringOfRequestAndEvaluation_neverPrintsTheAnswer() {
        PracticeAiScoringService service = serviceWith(scorerFor(Set.of("WRITE_ESSAY"), r -> result(64, false)));
        PracticeScoringRequest request = request("WRITE_ESSAY", "secret essay text");

        assertThat(request.toString()).doesNotContain("secret");
        assertThat(service.score(request).toString()).doesNotContain("spoken words").doesNotContain("{\"k\":1}");
    }
}
