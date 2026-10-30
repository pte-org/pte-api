package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.RecordingHttpServer;
import com.pte.scoring.internal.vendor.RecordingHttpServer.RecordedRequest;
import com.pte.scoring.internal.vendor.audio.TestAudio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureSpeechRestClientTest {

    private static final String KEY = "test-azure-key";
    private static final String PROVIDER_DETAIL = "provider-detail-must-not-leak";

    static final String NESTED_SUCCESS = """
            {"RecognitionStatus":"Success","Offset":400000,"Duration":11000000,
             "NBest":[{"Confidence":0.87,"Lexical":"good morning","ITN":"good morning",
               "MaskedITN":"good morning","Display":"Good morning.",
               "PronunciationAssessment":{"AccuracyScore":92.0,"FluencyScore":81.0,
                 "CompletenessScore":100.0,"PronScore":86.4,"ProsodyScore":78.0},
               "Words":[
                 {"Word":"good","Offset":500000,"Duration":2700000,
                  "PronunciationAssessment":{"AccuracyScore":96.0,"ErrorType":"None"}},
                 {"Word":"morning","Offset":3300000,"Duration":5500000,
                  "PronunciationAssessment":{"AccuracyScore":88.0,"ErrorType":"Mispronunciation"}}]}]}
            """;

    private static final String FLAT_SUCCESS = """
            {"RecognitionStatus":"Success","Offset":0,"Duration":9000000,
             "NBest":[{"Lexical":"good morning","Display":"Good morning.",
               "AccuracyScore":90.0,"FluencyScore":80.0,"CompletenessScore":100.0,
               "PronScore":85.0,"ProsodyScore":70.0,
               "Words":[
                 {"Word":"good","Offset":0,"Duration":3000000,"AccuracyScore":95.0,"ErrorType":"None"},
                 {"Word":"morning","Offset":3000000,"Duration":5000000,"AccuracyScore":85.0,"ErrorType":"None"}]}]}
            """;

    static final String RECOGNITION_SUCCESS = """
            {"RecognitionStatus":"Success","Offset":100000,"Duration":8000000,
             "NBest":[{"Confidence":0.9,"Lexical":"good morning","ITN":"good morning",
               "MaskedITN":"good morning","Display":"Good morning."}]}
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final byte[] wav = TestAudio.wav16kMono(TestAudio.speech(1));
    private RecordingHttpServer server;
    private AzureSpeechRestClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = RecordingHttpServer.start();
        client = clientWithTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void sendsTheAudioWithTheKeyInAHeaderAndAPronunciationAssessmentHeader() {
        server.enqueue(200, NESTED_SUCCESS);

        client.assess(wav, "Good morning.");

        RecordedRequest request = server.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().getPath()).isEqualTo("/speech/recognition/conversation/cognitiveservices/v1");
        assertThat(request.uri().getQuery()).contains("language=en-US").contains("format=detailed")
                .doesNotContain(KEY);
        assertThat(request.header("Ocp-Apim-Subscription-Key")).isEqualTo(KEY);
        assertThat(request.header("Content-Type")).isEqualTo("audio/wav; codecs=audio/pcm; samplerate=16000");
        assertThat(request.body()).isEqualTo(wav);

        JsonNode assessment = pronunciationAssessment(request);
        assertThat(assessment.path("ReferenceText").asText()).isEqualTo("Good morning.");
        assertThat(assessment.path("GradingSystem").asText()).isEqualTo("HundredMark");
        assertThat(assessment.path("Granularity").asText()).isEqualTo("Word");
        assertThat(assessment.path("Dimension").asText()).isEqualTo("Comprehensive");
        assertThat(assessment.path("EnableMiscue").asBoolean()).isFalse();
        assertThat(assessment.path("EnableProsodyAssessment").asBoolean()).isTrue();
    }

    @Test
    void unscriptedAssessmentSendsAnEmptyReferenceText() {
        server.enqueue(200, NESTED_SUCCESS);

        client.assess(wav, "");

        JsonNode assessment = pronunciationAssessment(server.requests().getFirst());
        assertThat(assessment.path("ReferenceText").isTextual()).isTrue();
        assertThat(assessment.path("ReferenceText").asText()).isEmpty();
    }

    @Test
    void recognitionOnlySendsNoAssessmentHeaderAndReturnsNoScores() {
        server.enqueue(200, RECOGNITION_SUCCESS);

        SpeechSegmentResult result = client.recognize(wav);

        assertThat(server.requests().getFirst().header("Pronunciation-Assessment")).isNull();
        assertThat(result.speechDetected()).isTrue();
        assertThat(result.displayText()).isEqualTo("Good morning.");
        assertThat(result.lexicalText()).isEqualTo("good morning");
        assertThat(result.scores()).isNull();
        assertThat(result.words()).isEmpty();
    }

    @Test
    void guardedClientRetriesATransientFailureOnce() {
        server.enqueue(503, PROVIDER_DETAIL);
        server.enqueue(200, NESTED_SUCCESS);
        List<Duration> waits = new ArrayList<>();
        AzureSpeechRestClient guarded = new AzureSpeechRestClient(httpClient, JSON, server.uri(""), KEY,
                Duration.ofSeconds(5), new ProviderGuard("Azure Speech", 3, 5, com.pte.TestClocks.fixed(), waits::add));

        SpeechSegmentResult result = guarded.assess(wav, "Good morning.");

        assertThat(result.displayText()).isEqualTo("Good morning.");
        assertThat(server.requests()).hasSize(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void parsesScoresNestedUnderPronunciationAssessment() {
        server.enqueue(200, NESTED_SUCCESS);

        SpeechSegmentResult result = client.assess(wav, "Good morning.");

        assertThat(result.speechDetected()).isTrue();
        assertThat(result.displayText()).isEqualTo("Good morning.");
        assertThat(result.lexicalText()).isEqualTo("good morning");
        assertThat(result.offsetTicks()).isEqualTo(400_000L);
        assertThat(result.durationTicks()).isEqualTo(11_000_000L);
        assertThat(result.scores()).isEqualTo(new PronunciationScores(92.0, 81.0, 100.0, 78.0, 86.4));
        assertThat(result.words()).containsExactly(
                new SpeechWord("good", 500_000L, 2_700_000L, 96.0, "None"),
                new SpeechWord("morning", 3_300_000L, 5_500_000L, 88.0, "Mispronunciation"));
    }

    @Test
    void parsesFlatScores() {
        server.enqueue(200, FLAT_SUCCESS);

        SpeechSegmentResult result = client.assess(wav, "Good morning.");

        assertThat(result.scores()).isEqualTo(new PronunciationScores(90.0, 80.0, 100.0, 70.0, 85.0));
        assertThat(result.words()).extracting(SpeechWord::accuracy).containsExactly(95.0, 85.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NoMatch", "InitialSilenceTimeout", "BabbleTimeout"})
    void treatsNoSpeechAsAValidZeroScore(String status) {
        server.enqueue(200, "{\"RecognitionStatus\":\"" + status + "\",\"Offset\":0,\"Duration\":0}");

        SpeechSegmentResult result = client.assess(wav, "Good morning.");

        assertThat(result.speechDetected()).isFalse();
        assertThat(result.displayText()).isEmpty();
        assertThat(result.words()).isEmpty();
        assertThat(result.scores()).isEqualTo(new PronunciationScores(0.0, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void recognitionOnlyNoSpeechHasNoScores() {
        server.enqueue(200, "{\"RecognitionStatus\":\"NoMatch\"}");

        SpeechSegmentResult result = client.recognize(wav);

        assertThat(result.speechDetected()).isFalse();
        assertThat(result.scores()).isNull();
    }

    @Test
    void classifiesHttpFailuresWithoutCopyingTheBody() {
        server.enqueue(401, "{\"error\":\"" + PROVIDER_DETAIL + "\"}");

        assertThatThrownBy(() -> client.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_AUTH_FAILED);
                    assertThat(ex.getMessage()).contains("Azure Speech").doesNotContain(PROVIDER_DETAIL);
                });
    }

    @Test
    void readsTheRetryAfterHeaderOnRateLimit() {
        server.enqueue(429, Map.of("Retry-After", "7"), "");

        assertThatThrownBy(() -> client.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(7)));
    }

    @Test
    void recognitionErrorStatusIsTransient() {
        server.enqueue(200, "{\"RecognitionStatus\":\"Error\"}");

        assertThatThrownBy(() -> client.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_UNAVAILABLE);
                    assertThat(ex.isTransient()).isTrue();
                });
    }

    @Test
    void unreadableBodyIsAPermanentInvalidResponseThatHidesTheBody() {
        server.enqueue(200, "not json " + PROVIDER_DETAIL);

        assertThatThrownBy(() -> client.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
                    assertThat(ex.isTransient()).isFalse();
                    assertThat(ex.getMessage()).doesNotContain(PROVIDER_DETAIL);
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void slowProviderTimesOutAsTransient() {
        AzureSpeechRestClient impatient = clientWithTimeout(Duration.ofMillis(200));
        server.enqueueDelayed(1_000, 200, NESTED_SUCCESS);

        assertThatThrownBy(() -> impatient.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT);
                    assertThat(ex.isTransient()).isTrue();
                });
    }

    @Test
    void unreachableProviderIsATransientNetworkError() throws IOException {
        URI closed;
        try (RecordingHttpServer gone = RecordingHttpServer.start()) {
            closed = gone.uri("");
        }
        AzureSpeechRestClient unreachable = new AzureSpeechRestClient(
                httpClient, JSON, closed, KEY, Duration.ofSeconds(2));

        assertThatThrownBy(() -> unreachable.assess(wav, "Good morning."))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_NETWORK_ERROR);
                    assertThat(ex.isTransient()).isTrue();
                });
    }

    private AzureSpeechRestClient clientWithTimeout(Duration timeout) {
        return new AzureSpeechRestClient(httpClient, JSON, server.uri(""), KEY, timeout);
    }

    static JsonNode pronunciationAssessment(RecordedRequest request) {
        return JSON.readTree(Base64.getDecoder().decode(request.header("Pronunciation-Assessment")));
    }
}
