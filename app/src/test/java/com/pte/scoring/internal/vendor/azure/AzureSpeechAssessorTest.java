package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.RecordingHttpServer;
import com.pte.scoring.internal.vendor.RecordingHttpServer.RecordedRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.audio.TestAudio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureSpeechAssessorTest {

    private RecordingHttpServer server;
    private AzureSpeechAssessor assessor;

    @BeforeEach
    void startServer() throws IOException {
        server = RecordingHttpServer.start();
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        assessor = new AzureSpeechAssessor(new AzureSpeechRestClient(
                httpClient, JsonMapper.builder().build(), server.uri(""), "test-azure-key", Duration.ofSeconds(5)));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void scoresLongAudioOneChunkAtATimeWithTheFullReference() {
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        byte[] audio = TestAudio.wav16kMono(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(17.7));

        SpeechAssessment result = assessor.assess(assessor.prepare(audio), "the full passage");

        assertThat(server.requests()).hasSize(2);
        assertThat(server.requests()).extracting(request -> request.body().length - 44)
                .containsExactly(354_400 * 2, (640_000 - 354_400) * 2);
        assertThat(server.requests()).extracting(this::referenceText)
                .containsExactly("the full passage", "the full passage");
        assertThat(result.segments()).hasSize(2);
        assertThat(result.displayText()).isEqualTo("Good morning. Good morning.");
        assertThat(result.audioDurationMs()).isEqualTo(40_000L);
    }

    @Test
    void scoresShortAudioWithOneCall() {
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);

        SpeechAssessment result = assessor.assess(
                assessor.prepare(TestAudio.wav16kMono(TestAudio.speech(5))), "Good morning.");

        assertThat(server.requests()).hasSize(1);
        assertThat(result.accuracy()).isEqualTo(92.0);
        assertThat(result.fluency()).isEqualTo(81.0);
        assertThat(result.prosody()).isEqualTo(78.0);
    }

    @Test
    void scoresAnEmptyRecordingAsNoSpeechWithoutCallingAzure() {
        SpeechAssessment result = assessor.assess(
                assessor.prepare(TestAudio.wav16kMono(new short[0])), "Good morning.");

        assertThat(server.requests()).isEmpty();
        assertThat(result.speechDetected()).isFalse();
        assertThat(result.accuracy()).isZero();
    }

    @Test
    void transcribeUsesRecognitionOnly() {
        server.enqueue(200, AzureSpeechRestClientTest.RECOGNITION_SUCCESS);

        SpeechAssessment result = assessor.transcribe(assessor.prepare(TestAudio.wav16kMono(TestAudio.speech(3))));

        assertThat(server.requests().getFirst().header("Pronunciation-Assessment")).isNull();
        assertThat(result.displayText()).isEqualTo("Good morning.");
        assertThat(result.accuracy()).isNull();
    }

    @Test
    void stopsAtTheFirstFailedChunk() {
        server.enqueue(503, "");
        List<AudioChunk> chunks = assessor.prepare(
                TestAudio.wav16kMono(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(17.7)));

        assertThatThrownBy(() -> assessor.assess(chunks, "the full passage"))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_UNAVAILABLE));
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void scriptedShortAudioRecognizesOnceThenAssessesAgainstTheWholeReference() {
        server.enqueue(200, RECOGNITION_OF.formatted("Many universities invest", "Many universities invest."));
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);

        ScriptedAssessment result = assessor.assessScripted(
                assessor.prepare(TestAudio.wav16kMono(TestAudio.speech(5))), "Many universities invest.");

        assertThat(server.requests()).hasSize(2);
        assertThat(server.requests().get(0).header("Pronunciation-Assessment")).isNull();
        assertThat(referenceText(server.requests().get(1))).isEqualTo("Many universities invest.");
        assertThat(result.heard().displayText()).isEqualTo("Many universities invest.");
        assertThat(result.assessed().accuracy()).isEqualTo(92.0);
    }

    @Test
    void scriptedLongAudioAssessesEachChunkAgainstTheReferenceItWasHeardSaying() {
        server.enqueue(200, RECOGNITION_OF.formatted("one two three", "One two three."));
        server.enqueue(200, RECOGNITION_OF.formatted("four five six", "Four five six."));
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        byte[] audio = TestAudio.wav16kMono(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(17.7));

        ScriptedAssessment result = assessor.assessScripted(assessor.prepare(audio), "One two three four five six.");

        assertThat(server.requests()).hasSize(4);
        assertThat(referenceText(server.requests().get(2))).isEqualTo("One two three");
        assertThat(referenceText(server.requests().get(3))).isEqualTo("four five six.");
        assertThat(result.heard().displayText()).isEqualTo("One two three. Four five six.");
        assertThat(result.assessed().segments()).hasSize(2);
    }

    @Test
    void scriptedMiddleChunkWithNoSpeechIsNotAssessedAndDragsTheScoreDown() {
        server.enqueue(200, RECOGNITION_OF.formatted("one two three", "One two three."));
        server.enqueue(200, "{\"RecognitionStatus\":\"NoMatch\"}");
        server.enqueue(200, RECOGNITION_OF.formatted("four five six", "Four five six."));
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);

        ScriptedAssessment result = assessor.assessScripted(assessor.prepare(threeLongSpeechChunks()),
                "One two three four five six.");

        assertThat(server.requests()).hasSize(5);
        assertThat(referenceText(server.requests().get(3))).isEqualTo("One two three");
        assertThat(referenceText(server.requests().get(4))).isEqualTo("four five six.");
        assertThat(result.assessed().segments()).hasSize(3);
        assertThat(result.assessed().segments().get(1).speechDetected()).isFalse();
        assertThat(result.assessed().fluency()).isLessThan(81.0);
    }

    @Test
    void scriptedMiddleChunkThatMatchesNoReferenceWordIsNotAssessedUnscripted() {
        server.enqueue(200, RECOGNITION_OF.formatted("one two three", "One two three."));
        server.enqueue(200, RECOGNITION_OF.formatted("blah blah", "Blah blah."));
        server.enqueue(200, RECOGNITION_OF.formatted("four five six", "Four five six."));
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);
        server.enqueue(200, AzureSpeechRestClientTest.NESTED_SUCCESS);

        ScriptedAssessment result = assessor.assessScripted(assessor.prepare(threeLongSpeechChunks()),
                "One two three four five six.");

        // An empty reference would make Azure assess the chatter against itself and rate it well.
        assertThat(server.requests()).hasSize(5);
        assertThat(server.requests().stream().skip(3).map(this::referenceText)).doesNotContain("");
        assertThat(result.heard().displayText()).isEqualTo("One two three. Blah blah. Four five six.");
        assertThat(result.assessed().segments().get(1).speechDetected()).isFalse();
    }

    @Test
    void scriptedEmptyRecordingCallsNothing() {
        ScriptedAssessment result = assessor.assessScripted(
                assessor.prepare(TestAudio.wav16kMono(new short[0])), "Good morning.");

        assertThat(server.requests()).isEmpty();
        assertThat(result.heard().speechDetected()).isFalse();
        assertThat(result.assessed().accuracy()).isZero();
    }

    private static byte[] threeLongSpeechChunks() {
        return TestAudio.wav16kMono(TestAudio.speech(22), TestAudio.silence(0.3), TestAudio.speech(22),
                TestAudio.silence(0.3), TestAudio.speech(22));
    }

    private static final String RECOGNITION_OF = """
            {"RecognitionStatus":"Success","Offset":100000,"Duration":8000000,
             "NBest":[{"Confidence":0.9,"Lexical":"%s","ITN":"%1$s","MaskedITN":"%1$s","Display":"%s"}]}
            """;

    private String referenceText(RecordedRequest request) {
        return AzureSpeechRestClientTest.pronunciationAssessment(request).path("ReferenceText").asText();
    }
}
