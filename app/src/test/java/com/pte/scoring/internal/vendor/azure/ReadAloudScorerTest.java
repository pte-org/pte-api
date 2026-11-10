package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitScoreSet;
import com.pte.scoring.support.SpeakingRubrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
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

/**
 * Read Aloud through the rubric: Content = N passage words minus replaced, omitted and inserted words; Pronunciation
 * and Oral Fluency from the uncalibrated Azure band mapping; item score from RubricScorer with N stored.
 */
@ExtendWith(MockitoExtension.class)
class ReadAloudScorerTest {

    private static final String PASSAGE = "Solar power is becoming one of the most affordable sources of energy.";
    private static final int PASSAGE_WORDS = 12;
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
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
    void aFullReadingScoresThroughTheRubricWithPearsonTraitKeys() {
        givenAudioHeardAs(PASSAGE, assessed(95.0, 89.0, 83.0));

        AiScoreResult result = scorer.score(request(PASSAGE));

        // Content 12/12, Pronunciation band 5 (accuracy 95), Oral Fluency band 4 ((89 + 83) / 2 = 86)
        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 12), Map.entry("PRONUNCIATION", 5),
                Map.entry("ORAL_FLUENCY", 4));
        assertThat(result.rawScore()).isEqualTo(rubricScore(12, 5, 4, PASSAGE_WORDS)).isEqualTo(95);
        assertThat(result.rubricVersionPublicId()).isEqualTo(VERSION);
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(result.provider()).isEqualTo(AzureScorers.PROVIDER);
        assertThat(result.transcript()).isEqualTo(PASSAGE);
    }

    @Test
    void omittedReplacedAndInsertedWordsAreAllDeductedFromContent() {
        // "becoming" -> "become" (replacement), "of the" omitted, "very" inserted: 4 errors.
        givenAudioHeardAs("Solar power is become one most very affordable sources of energy",
                assessed(90.0, 90.0, 90.0));

        AiScoreResult result = scorer.score(request(PASSAGE));

        assertThat(result.subScores().get("CONTENT")).isEqualTo(8);
        JsonNode content = detail(result).path("readAloudContent");
        assertThat(content.path("replacements").asInt()).isEqualTo(1);
        assertThat(content.path("omissions").asInt()).isEqualTo(2);
        assertThat(content.path("insertions").asInt()).isEqualTo(1);
    }

    @Test
    void theWordCountIsStoredSoTheItemCanBeRescoredWithoutThePassage() {
        givenAudioHeardAs(PASSAGE, assessed(95.0, 89.0, 83.0));

        JsonNode detail = detail(scorer.score(request(PASSAGE)));

        assertThat(detail.path("wordCount").asInt()).isEqualTo(PASSAGE_WORDS);
        assertThat(detail.path("readAloudContent").path("wordCount").asInt()).isEqualTo(PASSAGE_WORDS);
    }

    @Test
    void detailKeepsAzureRawScoresWordErrorsOmissionsAndTheUncalibratedMapping() {
        givenAudioHeardAs("Solar power is becoming one of the most affordable sources of",
                assessed(80.0, 80.0, null, word("becoming", 42.0, "Mispronunciation"), word("solar", 98.0, "None")));

        JsonNode detail = detail(scorer.score(request(PASSAGE)));

        assertThat(detail.path("scoreLabel").asText()).isEqualTo("UNVALIDATED");
        assertThat(detail.path("wordErrors")).hasSize(1);
        assertThat(detail.path("wordErrors").get(0).path("word").asText()).isEqualTo("becoming");
        assertThat(detail.path("omittedWords")).extracting(JsonNode::asText).containsExactly("energy");
        assertThat(detail.path("speech").path("accuracy").asDouble()).isEqualTo(80.0);
        assertThat(detail.path("speech").path("azureBandMapping").path("calibrated").asBoolean(true)).isFalse();
        assertThat(detail.path("prosodyUnavailable").asBoolean()).isTrue();
    }

    @Test
    void theOldWeightedPronunciationFormulaNoLongerDecidesTheScore() {
        givenAudioHeardAs(PASSAGE, assessed(95.0, 89.0, 83.0));

        AiScoreResult result = scorer.score(request(PASSAGE));

        assertThat(result.subScores()).doesNotContainKeys("ACCURACY", "FLUENCY", "PROSODY", "COMPLETENESS");
    }

    @Test
    void silenceIsAValidZeroNotAFailure() {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, PASSAGE)).thenReturn(new ScriptedAssessment(heard(""),
                SpeechFixtures.silence()));

        AiScoreResult result = scorer.score(request(PASSAGE));

        assertThat(result.rawScore()).isZero();
        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 0), Map.entry("PRONUNCIATION", 0),
                Map.entry("ORAL_FLUENCY", 0));
        assertThat(result.transcript()).isEmpty();
    }

    @Test
    void aPassageWithoutWordsIsABrokenItemAndFailsBeforeAnyAudioIsLoaded() {
        assertThatThrownBy(() -> scorer.score(request("  ... ")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY));
        verify(audio, never()).load(any(), any());
    }

    @Test
    void aRequestWithoutARubricFailsClosedBeforeAnyAudioIsLoaded() {
        AiScoringRequest noRubric = new AiScoringRequest(ScoringConstants.TASK_TYPE_READ_ALOUD, MEDIA, PASSAGE, null,
                null, null, null, null, TENANT);

        assertThatThrownBy(() -> scorer.score(noRubric)).isInstanceOfSatisfying(AiProviderException.class,
                ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.AI_RUBRIC_MISSING));
        verify(audio, never()).load(any(), any());
    }

    private void givenAudioHeardAs(String transcript, SpeechAssessment assessed) {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, PASSAGE)).thenReturn(new ScriptedAssessment(heard(transcript), assessed));
    }

    private static int rubricScore(int content, int pronunciation, int fluency, int wordCount) {
        return RubricScorer.score(SpeakingRubrics.of(ScoringConstants.TASK_TYPE_READ_ALOUD), new TraitScoreSet(
                Map.of("CONTENT", content, "PRONUNCIATION", pronunciation, "ORAL_FLUENCY", fluency), wordCount))
                .itemScore();
    }

    private static AiScoringRequest request(String promptText) {
        return new AiScoringRequest(ScoringConstants.TASK_TYPE_READ_ALOUD, MEDIA, promptText, null, null, null,
                null, null, TENANT, SpeakingRubrics.of(ScoringConstants.TASK_TYPE_READ_ALOUD), VERSION);
    }

    private static JsonNode detail(AiScoreResult result) {
        return JSON.readTree(result.detailJson());
    }
}
