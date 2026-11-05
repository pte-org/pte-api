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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Repeat Sentence through the rubric: Content 0-3 by words in sequence, Pronunciation and Oral Fluency 0-5. */
@ExtendWith(MockitoExtension.class)
class RepeatSentenceScorerTest {

    private static final String REFERENCE = "Many universities are investing in renewable energy projects.";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String MEDIA = UUID.randomUUID().toString();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID VERSION = UUID.randomUUID();

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
    void contentIsBandedByWordsInSequenceAndFluencyCountsOnce() {
        // 6 of the 8 sentence words repeated in order: at least 50%, so Content 2.
        givenAudioHeardAs("Many universities investing in renewable projects.", assessed(90.0, 80.0, null));

        AiScoreResult result = scorer.score(request());

        // Pronunciation band 5 (accuracy 90), Oral Fluency band 4 (fluency 80, no prosody); (2 + 5 + 4) / 13
        assertThat(result.subScores()).containsOnly(Map.entry("CONTENT", 2), Map.entry("PRONUNCIATION", 5),
                Map.entry("ORAL_FLUENCY", 4));
        assertThat(result.rawScore()).isEqualTo(rubricScore(2, 5, 4)).isEqualTo(85);
        assertThat(result.rubricVersionPublicId()).isEqualTo(VERSION);
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(result.transcript()).isEqualTo("Many universities investing in renewable projects.");
    }

    @Test
    void contentBandsThreeTwoOneAndZero() {
        assertThat(contentFor(REFERENCE)).isEqualTo(3);
        assertThat(contentFor("Many universities are investing")).isEqualTo(2);       // 4 of 8
        assertThat(contentFor("Many universities are")).isEqualTo(1);                 // 3 of 8
        assertThat(contentFor("Something else entirely")).isZero();
    }

    @Test
    void detailKeepsTheAlignmentTheContentCountAndIsLabelledUnvalidated() {
        givenAudioHeardAs("Many universities investing in renewable projects.", assessed(90.0, 80.0, null));

        JsonNode detail = JSON.readTree(scorer.score(request()).detailJson());

        assertThat(detail.path("scoreLabel").asText()).isEqualTo("UNVALIDATED");
        assertThat(detail.path("alignment").path("missingTokens")).extracting(JsonNode::asText)
                .containsExactly("are", "energy");
        assertThat(detail.path("repeatSentenceContent").path("wordsInSequence").asInt()).isEqualTo(6);
        assertThat(detail.path("repeatSentenceContent").path("sentenceWords").asInt()).isEqualTo(8);
        assertThat(detail.path("prosodyUnavailable").asBoolean()).isTrue();
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
    void aRequestWithoutARubricFailsClosed() {
        AiScoringRequest noRubric = new AiScoringRequest(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, MEDIA, null,
                REFERENCE, null, null, null, null, TENANT);

        assertThatThrownBy(() -> scorer.score(noRubric)).isInstanceOfSatisfying(AiProviderException.class,
                ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.AI_RUBRIC_MISSING));
    }

    private int contentFor(String heard) {
        org.mockito.Mockito.reset(audio, assessor);
        givenAudioHeardAs(heard, assessed(90.0, 80.0, 80.0));
        return scorer.score(request()).subScores().get("CONTENT");
    }

    private static int rubricScore(int content, int pronunciation, int fluency) {
        return RubricScorer.score(SpeakingRubrics.of(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE), new TraitScoreSet(
                Map.of("CONTENT", content, "PRONUNCIATION", pronunciation, "ORAL_FLUENCY", fluency), null))
                .itemScore();
    }

    @Test
    void missingReferenceAnswerTextIsRejected() {
        AiScoringRequest noReference = new AiScoringRequest(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, MEDIA,
                null, null, " ", null, null, null, TENANT,
                SpeakingRubrics.of(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE), VERSION);

        assertThatThrownBy(() -> scorer.score(noReference)).isInstanceOf(AiProviderException.class);
    }

    private void givenAudioHeardAs(String transcript, SpeechAssessment assessed) {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.assessScripted(chunks, REFERENCE)).thenReturn(new ScriptedAssessment(heard(transcript), assessed));
    }

    private static AiScoringRequest request() {
        return new AiScoringRequest(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, MEDIA, null, REFERENCE, null, null,
                null, null, TENANT, SpeakingRubrics.of(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE), VERSION);
    }
}
