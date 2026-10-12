package com.pte.scoring.internal.vendor;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.AzureSpeechRestClient;
import com.pte.scoring.internal.vendor.azure.SpeechAggregator;
import com.pte.scoring.internal.vendor.azure.SpeechAssessment;
import com.pte.scoring.internal.vendor.azure.SpeechSegmentResult;
import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.SummarizeGroupDiscussionScorer;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.pte.scoring.internal.vendor.SpikeSupport.GEMINI_BASE;
import static com.pte.scoring.internal.vendor.SpikeSupport.HTTP;
import static com.pte.scoring.internal.vendor.SpikeSupport.JSON;
import static com.pte.scoring.internal.vendor.SpikeSupport.TIMEOUT;
import static com.pte.scoring.internal.vendor.SpikeSupport.azureSpeechClient;
import static com.pte.scoring.internal.vendor.SpikeSupport.env;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 1 spike against the real Azure Speech and Gemini APIs. It spends
 * Azure credit, so it runs only when {@code SCORING_AI_SPIKE=true}; keys come
 * from the same env vars the app reads, and {@code SCORING_AI_SPIKE_DIR}
 * holds the POC samples converted to WAV plus {@code reference_passage.txt}.
 * Run one method at a time ({@code -Dtest=AzureGeminiSpikeTest#method}) to
 * keep each spend deliberate.
 */
@EnabledIfEnvironmentVariable(named = "SCORING_AI_SPIKE", matches = "true")
class AzureGeminiSpikeTest {

    // POC single-shot SDK scores from ai-research/samples/results.csv: accuracy, fluency, prosody, completeness.
    private static final Map<String, double[]> POC_READ_ALOUD = Map.of(
            "Ver1", new double[]{95.0, 89.0, 83.0, 95.0},
            "Ver2", new double[]{48.0, 74.0, 67.3, 48.0},
            "Ver3", new double[]{76.0, 87.0, 72.7, 78.0});

    private final Path dir = Path.of(env("SCORING_AI_SPIKE_DIR"));
    private final AzureSpeechRestClient azure = azureSpeechClient();
    private final AzureSpeechAssessor assessor = new AzureSpeechAssessor(azure);

    /** Ver1–3 (34–38 s, two chunks each): A = full reference per chunk, B = recognize, then per-chunk reference. */
    @Test
    void readAloudApproachAAndB() throws Exception {
        String reference = Files.readString(dir.resolve("reference_passage.txt")).trim();
        for (String name : List.of("Ver1", "Ver2", "Ver3")) {
            byte[] wav = Files.readAllBytes(dir.resolve(name + ".wav"));
            List<AudioChunk> chunks = assessor.prepare(wav);
            printFormat(name, wav, chunks);

            long start = System.nanoTime();
            SpeechAssessment approachA = assessor.assess(chunks, reference);
            report(name + " A", approachA, start, chunks.size());

            start = System.nanoTime();
            SpeechAssessment approachB = perChunkReference(chunks, reference);
            report(name + " B", approachB, start, chunks.size() * 2);

            double[] poc = POC_READ_ALOUD.get(name);
            System.out.printf(Locale.ROOT, "%-16s acc=%.1f flu=%.1f pro=%.1f compl=%.1f%n",
                    name + " POC", poc[0], poc[1], poc[2], poc[3]);
            assertThat(approachA.speechDetected()).isTrue();
        }
    }

    /** describe_good (39 s): empty ReferenceText, then 2-pass with each chunk's own transcript as reference. */
    @Test
    void unscriptedEmptyReferenceAndTwoPass() throws Exception {
        byte[] wav = Files.readAllBytes(dir.resolve("describe_good.wav"));
        List<AudioChunk> chunks = assessor.prepare(wav);
        printFormat("describe_good", wav, chunks);

        long start = System.nanoTime();
        SpeechAssessment emptyReference = assessor.assess(chunks, "");
        report("DI empty-ref", emptyReference, start, chunks.size());

        start = System.nanoTime();
        SpeechAssessment twoPass = ownTranscriptReference(chunks);
        report("DI 2-pass", twoPass, start, chunks.size() * 2);
        System.out.println("DI POC           flu=98.7 pro=90.8");
        assertThat(emptyReference.speechDetected()).isTrue();
    }

    /** lecture_source (98.6 s) stands in for a ~2 min SGD answer: chunk count and latency. */
    @Test
    void longUnscriptedAnswer() throws Exception {
        byte[] wav = Files.readAllBytes(dir.resolve("lecture_source.wav"));
        List<AudioChunk> chunks = assessor.prepare(wav);
        printFormat("lecture_source", wav, chunks);

        long start = System.nanoTime();
        SpeechAssessment result = assessor.assess(chunks, "");
        report("SGD-like", result, start, chunks.size());
        assertThat(result.speechDetected()).isTrue();
    }

    /**
     * The real SGD scorer (Azure hears, Gemini judges) on the 45 s recording against the labeled source.
     * One Azure call per chunk plus one Gemini call; prints counts and scores, never the transcript.
     */
    @Test
    void summarizeGroupDiscussionEndToEnd() throws Exception {
        byte[] wav = Files.readAllBytes(dir.resolve("audio/remaining/group_discussion_good.wav"));
        String source = Files.readString(dir.resolve("remaining/group_discussion/source_labeled.txt")).trim();
        SpeechAudioLoader loader = mock(SpeechAudioLoader.class);
        when(loader.load(anyString(), any())).thenReturn(wav);
        GeminiRestClient gemini = new GeminiRestClient(HTTP, JSON, GEMINI_BASE, env("SCORING_AI_GEMINI_API_KEY"),
                System.getenv().getOrDefault("SCORING_AI_GEMINI_MODEL", "gemini-flash-lite-latest"), TIMEOUT);
        SummarizeGroupDiscussionScorer scorer = new SummarizeGroupDiscussionScorer(loader, assessor, gemini, JSON);

        UUID tenant = UUID.randomUUID();
        long start = System.nanoTime();
        AiScoreResult result = scorer.score(new AiScoringRequest(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION,
                UUID.randomUUID().toString(), null, source, null, null, null, null, tenant));
        long elapsed = elapsedMs(start);

        JsonNode detail = JSON.readTree(result.detailJson());
        System.out.printf(Locale.ROOT, "SGD good: raw=%d sub=%s fallback=%s model=%s time=%dms%n",
                result.rawScore(), result.subScores(), result.fallback(), result.model(), elapsed);
        System.out.printf(Locale.ROOT, "SGD good: transcript words=%d speech=%s covered=%d missed=%d "
                        + "speakers=%d relationships=%d%n",
                words(result.transcript()).size(), detail.path("speech"),
                detail.path("covered_points").size(), detail.path("missed_points").size(),
                detail.path("speaker_contributions").size(), detail.path("relationships").size());
        System.out.println("SGD good: feedback: " + result.feedback());
        assertThat(result.rawScore()).isBetween(0, 100);
        assertThat(result.transcript()).isNotBlank();
    }

    /** Free tier: confirms the alias exists in the models list and one structured call honours the schema. */
    @Test
    void geminiStructuredCall() throws Exception {
        String model = System.getenv().getOrDefault("SCORING_AI_GEMINI_MODEL", "gemini-flash-lite-latest");
        HttpResponse<byte[]> models = HTTP.send(HttpRequest.newBuilder(
                                GEMINI_BASE.resolve("/v1beta/models?pageSize=1000"))
                        .header("x-goog-api-key", env("SCORING_AI_GEMINI_API_KEY"))
                        .timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        System.out.println("models list HTTP " + models.statusCode());
        List<String> liteModels = new ArrayList<>();
        for (JsonNode entry : JSON.readTree(models.body()).path("models")) {
            String name = entry.path("name").asText();
            if (name.contains("flash-lite")) {
                liteModels.add(name + " (version " + entry.path("version").asText("?") + ")");
            }
        }
        liteModels.forEach(name -> System.out.println("  " + name));
        assertThat(liteModels).anyMatch(name -> name.startsWith("models/" + model + " "));

        GeminiRestClient gemini = new GeminiRestClient(HTTP, JSON, GEMINI_BASE,
                env("SCORING_AI_GEMINI_API_KEY"), model, TIMEOUT);
        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "content", Map.of("type", "INTEGER", "description", "0, 1 or 2"),
                        "feedback", Map.of("type", "STRING")),
                "required", List.of("content", "feedback"));
        long start = System.nanoTime();
        GeminiResponse response = gemini.generate(new GeminiRequest(
                "You are a strict PTE Academic examiner. Score only the content trait.",
                "Task: summarize in one sentence that solar panels got cheaper, so more homes use them.\n"
                        + "Answer: Solar panels became much cheaper over the past decade, so many more "
                        + "households now install them.",
                schema, List.of()));
        System.out.printf(Locale.ROOT, "gemini %s in %d ms: content=%s feedback=%d chars%n",
                response.modelVersion(), elapsedMs(start), response.content().path("content"),
                response.content().path("feedback").asText().length());
        assertThat(response.content().path("content").isIntegralNumber()).isTrue();
    }

    private SpeechAssessment perChunkReference(List<AudioChunk> chunks, String reference) {
        List<String> referenceTokens = Arrays.asList(reference.split("\\s+"));
        List<List<String>> heard = new ArrayList<>();
        for (AudioChunk chunk : chunks) {
            heard.add(words(azure.recognize(chunk.wav()).lexicalText()));
        }
        List<String> normalizedReference = referenceTokens.stream().map(AzureGeminiSpikeTest::normalize).toList();
        // With EnableMiscue=false the assessed transcript is forced onto the reference, so only pass 1 shows omissions.
        System.out.printf(Locale.ROOT, "  pass-1 completeness %.1f%n",
                lcsTable(heard.stream().flatMap(List::stream).toList(), normalizedReference)[0][0]
                        * 100.0 / normalizedReference.size());
        int[] bounds = splitReference(heard, normalizedReference);
        List<SpeechSegmentResult> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String part = String.join(" ", referenceTokens.subList(bounds[i], bounds[i + 1]));
            System.out.printf(Locale.ROOT, "  chunk %d reference words %d..%d%n", i, bounds[i], bounds[i + 1]);
            results.add(azure.assess(chunks.get(i).wav(), part));
        }
        return SpeechAggregator.aggregate(chunks, results);
    }

    private SpeechAssessment ownTranscriptReference(List<AudioChunk> chunks) {
        List<SpeechSegmentResult> results = new ArrayList<>();
        for (AudioChunk chunk : chunks) {
            results.add(azure.assess(chunk.wav(), azure.recognize(chunk.wav()).displayText()));
        }
        return SpeechAggregator.aggregate(chunks, results);
    }

    /** Reference split points that follow the LCS alignment of each chunk's recognized words. */
    private static int[] splitReference(List<List<String>> heardByChunk, List<String> reference) {
        List<String> heard = new ArrayList<>();
        List<Integer> chunkOf = new ArrayList<>();
        for (int chunk = 0; chunk < heardByChunk.size(); chunk++) {
            for (String word : heardByChunk.get(chunk)) {
                heard.add(word);
                chunkOf.add(chunk);
            }
        }
        int[][] lcs = lcsTable(heard, reference);
        int[] lastMatched = new int[heardByChunk.size()];
        Arrays.fill(lastMatched, -1);
        int i = 0;
        int j = 0;
        while (i < heard.size() && j < reference.size()) {
            if (heard.get(i).equals(reference.get(j))) {
                lastMatched[chunkOf.get(i)] = j;
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        int[] bounds = new int[heardByChunk.size() + 1];
        for (int chunk = 0; chunk < heardByChunk.size() - 1; chunk++) {
            bounds[chunk + 1] = Math.max(bounds[chunk], lastMatched[chunk] + 1);
        }
        bounds[heardByChunk.size()] = reference.size();
        return bounds;
    }

    private static int[][] lcsTable(List<String> a, List<String> b) {
        int[][] table = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                table[i][j] = a.get(i).equals(b.get(j))
                        ? table[i + 1][j + 1] + 1
                        : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        return table;
    }

    private static List<String> words(String text) {
        return Arrays.stream(text.split("\\s+")).map(AzureGeminiSpikeTest::normalize)
                .filter(word -> !word.isEmpty()).toList();
    }

    private static String normalize(String token) {
        return token.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']", "");
    }

    private static void printFormat(String name, byte[] wav, List<AudioChunk> chunks) throws Exception {
        AudioFormat source = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(wav)).getFormat();
        System.out.printf(Locale.ROOT, "%s: source %.0f Hz x%d -> 16 kHz mono; chunks %s%n", name,
                source.getSampleRate(), source.getChannels(),
                chunks.stream().map(chunk -> chunk.offsetMs() + "+" + chunk.durationMs()).toList());
    }

    private static void report(String label, SpeechAssessment result, long startNanos, int calls) {
        Function<Double, String> fmt = value -> value == null ? "null" : String.format(Locale.ROOT, "%.1f", value);
        String transcript = result.displayText();
        System.out.printf(Locale.ROOT,
                "%-16s calls=%d audio=%.1fs time=%dms acc=%s flu=%s pro=%s words=%d speech=%s \"%s\"%n",
                label, calls, result.audioDurationMs() / 1000.0 * calls / Math.max(1, result.segments().size()),
                elapsedMs(startNanos), fmt.apply(result.accuracy()), fmt.apply(result.fluency()),
                fmt.apply(result.prosody()), words(transcript).size(),
                result.speechDetected(), transcript.substring(0, Math.min(60, transcript.length())));
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
