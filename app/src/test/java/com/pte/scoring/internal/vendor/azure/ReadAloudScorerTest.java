package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.assessed;
import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.heard;
import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.oneChunk;
import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.word;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadAloudScorerTest {

    private static final String PASSAGE = "Solar power is becoming one of the most affordable sources of energy.";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String MEDIA = UUID.randomUUID().toString();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SpeechAudioLoader audio;
    @Mock
    private AzureSpeechAssessor assessor;
    private ReadAloudScorer scorer;
    private final List<AudioChunk> chunks = oneChunk();

    @BeforeEach
    void setUp() {
        scorer = new ReadAloudScorer(audio, assessor, JSON);
    }

    @Test
    void scoresReadAloudTasksOnly() {
        assertThat(scorer.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_READ_ALOUD);
    }

    @Test
    void rawScoreIsThePronunciationScoreFromMergedAzureScoresAndTheBackendCompleteness() {
        givenAudioHeardAs(PASSAGE, assessed(95.0, 89.0, 83.0));

        AiScoreResult result = scorer.score(request(PASSAGE));

        // 0.4*95 + 0.2*83 + 0.2*89 + 0.2*100
        assertThat(result.rawScore()).isEqualTo(92);
        assertThat(result.subScores()).containsEntry("PRONUNCIATION", 92).containsEntry("ACCURACY", 95)
                .containsEntry("FLUENCY", 89).containsEntry("PROSODY", 83).containsEntry("COMPLETENESS", 100);
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(result.provider()).isEqualTo(AzureScorers.PROVIDER);
        assertThat(result.fallback()).isFalse();
        assertThat(result.transcript()).isEqualTo(PASSAGE);
    }

    @Test
    void completenessComesFromAligningTheRecognizedTranscriptNotFromTheScoredPass() {
        // The scored pass is forced onto the reference (EnableMiscue=false), so Azure reports 100 either way.
        givenAudioHeardAs("Solar power is becoming one of the most", assessed(90.0, 90.0, 90.0));

        AiScoreResult result = scorer.score(request(PASSAGE));

        assertThat(result.subScores().get("COMPLETENESS")).isEqualTo(67); // 8 of 12 words
        assertThat(result.rawScore()).isLessThan(90);
    }

    @Test
    void detailListsMispronouncedAndOmittedWordsAndIsLabelledUnvalidated() {
        givenAudioHeardAs("Solar power is becoming one of the most affordable sources of",
                assessed(80.0, 80.0, 80.0, word("becoming", 42.0, "Mispronunciation"), word("solar", 98.0, "None")));

        JsonNode detail = detail(scorer.score(request(PASSAGE)));

        assertThat(detail.path("scoreLabel").asText()).isEqualTo("UNVALIDATED");
        assertThat(detail.path("wordErrors")).hasSize(1);
        assertThat(detail.path("wordErrors").get(0).path("word").asText()).isEqualTo("becoming");
        assertThat(detail.path("wordErrors").get(0).path("errorType").asText()).isEqualTo("Mispronunciation");
        assertThat(detail.path("omittedWords")).extracting(JsonNode::asText).containsExactly("energy");
    }

    @Test
    void silenceIsAValidZeroNotAFailure() {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, PASSAGE)).thenReturn(new ScriptedAssessment(heard(""),
                SpeechFixtures.silence()));

        AiScoreResult result = scorer.score(request(PASSAGE));

        assertThat(result.rawScore()).isZero();
        assertThat(result.subScores().get("COMPLETENESS")).isZero();
        assertThat(result.transcript()).isEmpty();
    }

    @Test
    void aReferenceWithoutWordsFailsBeforeAnyAudioIsLoaded() {
        assertThatThrownBy(() -> scorer.score(request("  ... ")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY));
        verify(audio, never()).load(any(), any());
    }

    private void givenAudioHeardAs(String transcript, SpeechAssessment assessed) {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, PASSAGE)).thenReturn(new ScriptedAssessment(heard(transcript), assessed));
    }

    private static AiScoringRequest request(String promptText) {
        return new AiScoringRequest(ScoringConstants.TASK_TYPE_READ_ALOUD, MEDIA, promptText, null, null, null,
                null, null, TENANT);
    }

    private static JsonNode detail(AiScoreResult result) {
        return JSON.readTree(result.detailJson());
    }
}
