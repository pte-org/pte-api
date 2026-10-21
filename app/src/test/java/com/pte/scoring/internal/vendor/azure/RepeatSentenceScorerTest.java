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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepeatSentenceScorerTest {

    private static final String REFERENCE = "Many universities are investing in renewable energy projects.";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String MEDIA = UUID.randomUUID().toString();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SpeechAudioLoader audio;
    @Mock
    private AzureSpeechAssessor assessor;
    private RepeatSentenceScorer scorer;
    private final List<AudioChunk> chunks = oneChunk();

    @BeforeEach
    void setUp() {
        scorer = new RepeatSentenceScorer(audio, assessor, JSON);
    }

    @Test
    void scoresRepeatSentenceTasksOnly() {
        assertThat(scorer.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);
    }

    @Test
    void rawScoreAveragesAlignmentPronunciationAndFluency() {
        // Alignment F1 against the reference is 85.7 (the POC's value for this pair), recall 75.0.
        givenAudioHeardAs("Many universities investing in renewable projects.", assessed(90.0, 80.0, null));

        AiScoreResult result = scorer.score(request());

        // pronunciation = 0.5*90 + 0.25*80 + 0.25*75 = 83.75 (no prosody); mean(85.7, 83.75, 80) = 83.15
        assertThat(result.rawScore()).isEqualTo(83);
        assertThat(result.subScores()).containsEntry("CONTENT", 86).containsEntry("PRONUNCIATION", 84)
                .containsEntry("FLUENCY", 80);
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(result.transcript()).isEqualTo("Many universities investing in renewable projects.");
    }

    @Test
    void detailKeepsTheAlignmentAndIsLabelledUnvalidated() {
        givenAudioHeardAs("Many universities investing in renewable projects.", assessed(90.0, 80.0, null));

        JsonNode detail = JSON.readTree(scorer.score(request()).detailJson());

        assertThat(detail.path("scoreLabel").asText()).isEqualTo("UNVALIDATED");
        assertThat(detail.path("alignment").path("score").asDouble()).isEqualTo(85.7);
        assertThat(detail.path("alignment").path("missingTokens")).extracting(JsonNode::asText)
                .containsExactly("are", "energy");
        assertThat(detail.path("alignment").path("extraTokens")).isEmpty();
    }

    @Test
    void silenceScoresZeroInsteadOfFailing() {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, REFERENCE)).thenReturn(new ScriptedAssessment(heard(""),
                SpeechFixtures.silence()));

        assertThat(scorer.score(request()).rawScore()).isZero();
    }

    @Test
    void missingReferenceAnswerTextIsRejected() {
        AiScoringRequest noReference = new AiScoringRequest(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, MEDIA,
                null, null, " ", null, null, null, TENANT);

        assertThatThrownBy(() -> scorer.score(noReference)).isInstanceOf(AiProviderException.class);
    }

    private void givenAudioHeardAs(String transcript, SpeechAssessment assessed) {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, REFERENCE)).thenReturn(new ScriptedAssessment(heard(transcript), assessed));
    }

    private static AiScoringRequest request() {
        return new AiScoringRequest(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, MEDIA, null, REFERENCE, null, null,
                null, null, TENANT);
    }
}
