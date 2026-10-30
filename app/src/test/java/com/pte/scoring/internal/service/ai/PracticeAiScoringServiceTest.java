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
import com.pte.scoring.internal.vendor.AiAnswerScorer;
import com.pte.scoring.internal.vendor.AiJobBudget;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.EssayScoringClient;
import com.pte.scoring.internal.vendor.SpeechScoringClient;
import org.junit.jupiter.api.Test;
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

    private final AtomicReference<AiScoringRequest> seen = new AtomicReference<>();
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
        return new PracticeAiScoringService(scorers, speech, essay,
                new ObjectiveScoringService(JsonMapper.builder().build()), CLOCK, new AiProviderProperties());
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
        assertThat(evaluation.rawScore()).isEqualTo(64);
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

    @Test
    void speechTypeWithoutScorer_fallsBackToTheGenericSpeechClient() {
        AtomicReference<String> mediaId = new AtomicReference<>();
        PracticeAiScoringService service = service(List.of(), (media, ref, tenant) -> {
            mediaId.set(media);
            assertThat(ref).isEqualTo("correct");
            assertThat(tenant).isEqualTo(TENANT);
            return result(33, false);
        }, (text, prompt) -> {
            throw new AssertionError("essay client must not run for speech");
        });

        assertThat(service.score(request("READ_ALOUD", "media-9")).rawScore()).isEqualTo(33);
        assertThat(mediaId.get()).isEqualTo("media-9");
    }

    @Test
    void textTypeWithoutScorer_fallsBackToTheGenericEssayClient() {
        PracticeAiScoringService service = service(List.of(), (m, r, t) -> {
            throw new AssertionError("speech client must not run for text");
        }, (text, prompt) -> {
            assertThat(text).isEqualTo("my text");
            assertThat(prompt).isEqualTo("correct");
            return result(58, false);
        });

        assertThat(service.score(request("WRITE_ESSAY", "my text")).rawScore()).isEqualTo(58);
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
