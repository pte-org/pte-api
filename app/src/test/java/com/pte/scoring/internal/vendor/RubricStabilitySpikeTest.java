package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.prompt.MedianOfThreeScoring;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.RunOutcome;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.SampleRuns;
import com.pte.scoring.internal.rubric.stability.StabilityMetrics.StabilityReport;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset.SpeakingSample;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset.WritingSample;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.SpeechAssessment;
import com.pte.scoring.internal.vendor.azure.SpeechSegmentResult;
import com.pte.scoring.internal.vendor.gemini.PromptImageLoader;
import com.pte.scoring.internal.vendor.gemini.scorer.DescribeImageScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.RespondToSituationScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.RetellLectureScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeGroupDiscussionScorer;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.support.SpeakingRubrics;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeSpokenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.SummarizeWrittenTextScorer;
import com.pte.scoring.internal.vendor.gemini.scorer.WriteEssayScorer;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.pte.scoring.internal.vendor.SpikeSupport.GEMINI_BASE;
import static com.pte.scoring.internal.vendor.SpikeSupport.HTTP;
import static com.pte.scoring.internal.vendor.SpikeSupport.JSON;
import static com.pte.scoring.internal.vendor.SpikeSupport.TIMEOUT;
import static com.pte.scoring.internal.vendor.SpikeSupport.env;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 4 stability spike (plan quang-scoring-rubric-transparency): every synthetic writing sample is scored
 * {@link StabilityMetrics#RUNS} times by the real writing scorer (real Gemini, catalog rubric), and the share of LLM
 * trait scores whose spread across the runs is at most one level must reach 90%. Form is coded, so it is not
 * measured. Phase 5 adds the speaking transcripts: the real DI, RL, RTS and SGD scorers score CONTENT 0-6 from each
 * synthetic transcript (the Azure step is replaced by a stub that returns the transcript, so only Gemini is measured;
 * Describe Image therefore uses its reference-description fallback), against the same 90% gate.
 *
 * <p>Runs only with {@code SCORING_AI_SPIKE=true} and {@code SCORING_AI_GEMINI_API_KEY}; it spends Gemini quota
 * (20 samples x 3 runs, up to 3 schema attempts each). Optional: {@code SCORING_AI_GEMINI_MODEL};
 * {@code SCORING_AI_SPIKE_OUT} (file that receives the aggregate JSON); {@code SCORING_AI_SPIKE_MEDIAN_TRAITS}
 * (comma-separated trait keys, or {@code ALL}) makes each run the median of three calls, which measures the
 * fallback. Prints aggregate numbers only, never an answer, a feedback text or a sample id.
 */
@EnabledIfEnvironmentVariable(named = "SCORING_AI_SPIKE", matches = "true")
class RubricStabilitySpikeTest {

    /** One sample to score repeatedly: who scores it, with which request, and which LLM traits are measured. */
    private record Case(String id, String taskType, AiAnswerScorer scorer, AiScoringRequest request,
                        List<String> llmTraits, Map<String, Integer> intendedBands) {
    }

    private static final String MEDIAN_ALL = "ALL";
    private static final String SCHEMA_ATTEMPTS = "schemaAttempts";

    private final RubricCatalog catalog = new RubricCatalogImpl();
    private final String model = System.getenv().getOrDefault("SCORING_AI_GEMINI_MODEL", "gemini-flash-lite-latest");
    private final GeminiRestClient gemini = new GeminiRestClient(HTTP, JSON, GEMINI_BASE,
            env("SCORING_AI_GEMINI_API_KEY"), model, TIMEOUT);
    private final Map<String, AiAnswerScorer> scorers = Map.of(
            ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, new SummarizeWrittenTextScorer(gemini, JSON),
            ScoringConstants.TASK_TYPE_WRITE_ESSAY, new WriteEssayScorer(gemini, JSON),
            ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, new SummarizeSpokenTextScorer(gemini, JSON));
    private final String medianSetting = System.getenv().getOrDefault("SCORING_AI_SPIKE_MEDIAN_TRAITS", "");
    private final UUID tenant = UUID.randomUUID();

    private final SpeechAudioLoader loader = mock(SpeechAudioLoader.class);
    private final AzureSpeechAssessor assessor = mock(AzureSpeechAssessor.class);
    private final Map<String, String> transcriptsById = new LinkedHashMap<>();
    private final Map<String, AiAnswerScorer> speakingScorers = Map.of(
            ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, new DescribeImageScorer(loader, assessor, gemini, JSON,
                    mock(PromptImageLoader.class)),
            ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, new RetellLectureScorer(loader, assessor, gemini, JSON),
            ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION,
            new RespondToSituationScorer(loader, assessor, gemini, JSON),
            ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
            new SummarizeGroupDiscussionScorer(loader, assessor, gemini, JSON));

    /** The Azure step returns the synthetic transcript whose sample id is the payload; nothing else is faked. */
    RubricStabilitySpikeTest() {
        when(loader.load(anyString(), any())).thenAnswer(call -> call.<String>getArgument(0)
                .getBytes(StandardCharsets.UTF_8));
        when(assessor.prepare(any())).thenAnswer(call -> List.of(new AudioChunk(call.getArgument(0), 0, 5_000)));
        when(assessor.assess(any(), eq(""))).thenAnswer(call -> {
            List<AudioChunk> chunks = call.getArgument(0);
            String transcript = transcriptsById.get(new String(chunks.getFirst().wav(), StandardCharsets.UTF_8));
            SpeechSegmentResult segment = new SpeechSegmentResult(true, transcript, transcript, 0, 50_000_000L,
                    null, List.of());
            return new SpeechAssessment(80.0, 80.0, 80.0, List.of(), List.of(segment), 5_000);
        });
    }

    @Test
    void allWriting() {
        measure("allWriting", SyntheticDataset.WRITING_TASK_TYPES);
    }

    @Test
    void allSpeaking() {
        measureSpeaking("allSpeaking", SyntheticDataset.SPEAKING_TASK_TYPES);
    }

    @Test
    void describeImage() {
        measureSpeaking("describeImage", Set.of(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE));
    }

    @Test
    void retellLecture() {
        measureSpeaking("retellLecture", Set.of(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE));
    }

    @Test
    void respondToSituation() {
        measureSpeaking("respondToSituation", Set.of(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION));
    }

    @Test
    void summarizeGroupDiscussion() {
        measureSpeaking("summarizeGroupDiscussion", Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION));
    }

    @Test
    void summarizeWrittenText() {
        measure("summarizeWrittenText", Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT));
    }

    @Test
    void writeEssay() {
        measure("writeEssay", Set.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY));
    }

    @Test
    void summarizeSpokenText() {
        measure("summarizeSpokenText", Set.of(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT));
    }

    private void measure(String label, Set<String> taskTypes) {
        SyntheticDataset.Writing dataset = SyntheticDataset.writing();
        List<Case> cases = new ArrayList<>();
        for (WritingSample sample : dataset.samples()) {
            if (!taskTypes.contains(sample.taskType())) {
                continue;
            }
            TaskRubric task = catalog.find(sample.taskType()).orElseThrow();
            cases.add(new Case(sample.id(), sample.taskType(), scorers.get(sample.taskType()),
                    request(task, sample, dataset.sourceOf(sample).text()), llmTraits(task), sample.intendedBands()));
        }
        run(label, cases);
    }

    private void measureSpeaking(String label, Set<String> taskTypes) {
        SyntheticDataset.Speaking dataset = SyntheticDataset.speaking();
        List<Case> cases = new ArrayList<>();
        for (SpeakingSample sample : dataset.samples()) {
            if (!taskTypes.contains(sample.taskType())) {
                continue;
            }
            transcriptsById.put(sample.id(), sample.transcript());
            String source = dataset.sourceOf(sample).text();
            AiScoringRequest request = new AiScoringRequest(sample.taskType(), sample.id(), source, source, null,
                    null, null, null, tenant, SpeakingRubrics.of(sample.taskType()), UUID.randomUUID());
            cases.add(new Case(sample.id(), sample.taskType(), speakingScorers.get(sample.taskType()), request,
                    llmTraits(catalog.find(sample.taskType()).orElseThrow()), sample.intendedBands()));
        }
        run(label, cases);
    }

    private void run(String label, List<Case> cases) {
        List<SampleRuns> measured = new ArrayList<>();
        for (Case sample : cases) {
            List<RunOutcome> runs = new ArrayList<>();
            for (int run = 0; run < StabilityMetrics.RUNS; run++) {
                runs.add(medianTraits(sample.llmTraits()).isEmpty()
                        ? call(sample.scorer(), sample.request(), sample.llmTraits())
                        : medianRun(sample.scorer(), sample.request(), sample.llmTraits()));
            }
            measured.add(new SampleRuns(sample.id(), sample.taskType(), sample.llmTraits(), sample.intendedBands(),
                    runs));
        }
        StabilityReport report = StabilityMetrics.compute(measured);
        print(label, measured.size(), report);
        writeAggregates(label, measured.size(), report);
        assertThat(report.meetsGate())
                .as("stable share %.3f is below the %.2f gate", report.overall().stableRate(),
                        StabilityMetrics.GATE_THRESHOLD)
                .isTrue();
    }

    private static List<String> llmTraits(TaskRubric task) {
        return RubricPromptBuilder.llmTraits(task).stream().map(TraitDefinition::key).toList();
    }

    /** One real scorer call; a failure is recorded by its reason code only, never its message. */
    private RunOutcome call(AiAnswerScorer scorer, AiScoringRequest request, List<String> llmTraits) {
        long start = System.nanoTime();
        try {
            AiScoreResult result = scorer.score(request);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Map<String, Integer> traits = new LinkedHashMap<>();
            llmTraits.stream().filter(result.subScores()::containsKey)
                    .forEach(trait -> traits.put(trait, result.subScores().get(trait)));
            return RunOutcome.success(traits, schemaAttempts(result), elapsedMs);
        } catch (AiProviderException ex) {
            return RunOutcome.failure(ex.getReason().name(), (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException ex) {
            return RunOutcome.failure(ex.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
        }
    }

    /** The fallback under test: three calls folded by MedianOfThreeScoring; any failed call fails the run. */
    private RunOutcome medianRun(AiAnswerScorer scorer, AiScoringRequest request, List<String> llmTraits) {
        List<RunOutcome> calls = new ArrayList<>();
        for (int index = 0; index < MedianOfThreeScoring.RUNS; index++) {
            calls.add(call(scorer, request, llmTraits));
        }
        long latency = calls.stream().mapToLong(RunOutcome::latencyMs).sum();
        int attempts = calls.stream().mapToInt(RunOutcome::schemaAttempts).sum();
        RunOutcome failed = calls.stream().filter(RunOutcome::failed).findFirst().orElse(null);
        if (failed != null) {
            return RunOutcome.failure(failed.failureReason(), latency);
        }
        try {
            Map<String, Integer> combined = MedianOfThreeScoring.combine(
                    calls.stream().map(RunOutcome::traits).toList(), medianTraits(llmTraits));
            return RunOutcome.success(combined, attempts - (MedianOfThreeScoring.RUNS - 1), latency);
        } catch (AiProviderException ex) {
            return RunOutcome.failure(ex.getReason().name(), latency);
        }
    }

    private Set<String> medianTraits(List<String> llmTraits) {
        if (medianSetting.isBlank()) {
            return Set.of();
        }
        if (MEDIAN_ALL.equalsIgnoreCase(medianSetting.strip())) {
            return Set.copyOf(llmTraits);
        }
        return Arrays.stream(medianSetting.split(",")).map(String::strip).filter(llmTraits::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Catalog defaults equal the seeded first rubric version; the source goes where each scorer reads it. */
    private AiScoringRequest request(TaskRubric task, WritingSample sample, String source) {
        EffectiveRubric rubric = new EffectiveRubric(task, catalog.defaultParameters(task.taskType()));
        return new AiScoringRequest(sample.taskType(), sample.answer(), source, source, null, null, null, null,
                tenant, rubric, UUID.randomUUID());
    }

    private static int schemaAttempts(AiScoreResult result) {
        if (result.detailJson() == null) {
            return 1;
        }
        return JSON.readTree(result.detailJson()).path(SCHEMA_ATTEMPTS).path("count").asInt(1);
    }

    private void print(String label, int samples, StabilityReport report) {
        System.out.printf(Locale.ROOT, "%s: model=%s samples=%d runs=%d median=%s%n", label, model, samples,
                report.runs(), medianSetting.isBlank() ? "off" : medianSetting);
        report.byTrait().forEach((key, stat) -> System.out.printf(Locale.ROOT,
                "%s: %-45s stable=%d/%d (%.1f%%) complete=%d withinIntended=%d/%d (%.1f%%)%n", label, key,
                stat.stable(), stat.total(), 100 * stat.stableRate(), stat.complete(), stat.intendedWithinOne(),
                stat.intendedChecked(), 100 * stat.intendedWithinOneRate()));
        System.out.printf(Locale.ROOT, "%s: OVERALL stable=%d/%d (%.1f%%) gate>=%.0f%% met=%s%n", label,
                report.overall().stable(), report.overall().total(), 100 * report.overall().stableRate(),
                100 * StabilityMetrics.GATE_THRESHOLD, report.meetsGate());
        System.out.printf(Locale.ROOT, "%s: failedRuns=%d/%d reasons=%s runsWithRetry=%d extraAttempts=%d "
                        + "latencyAvgMs=%d latencyMaxMs=%d tokens=not exposed by GeminiRestClient%n", label,
                report.failedRuns(), report.runs(), report.failureReasons(), report.runsWithRetry(),
                report.extraAttempts(), report.latencyAverageMs(), report.latencyMaxMs());
    }

    private void writeAggregates(String label, int samples, StabilityReport report) {
        String out = System.getenv("SCORING_AI_SPIKE_OUT");
        if (out == null || out.isBlank()) {
            return;
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("label", label);
        document.put("model", model);
        document.put("samples", samples);
        document.put("runsPerSample", StabilityMetrics.RUNS);
        document.put("medianTraits", medianSetting.isBlank() ? "off" : medianSetting);
        document.put("tokens", "NOT_EXPOSED_BY_GEMINI_REST_CLIENT");
        document.put("aggregates", report.toAggregates());
        try {
            Files.writeString(Path.of(out), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
