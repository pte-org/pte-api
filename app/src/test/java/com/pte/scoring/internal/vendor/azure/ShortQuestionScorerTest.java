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

import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.heard;
import static com.pte.scoring.internal.vendor.azure.SpeechFixtures.oneChunk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortQuestionScorerTest {

    private static final String ANSWER_KEY = "astronomer / an astronomer";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String MEDIA = UUID.randomUUID().toString();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Mock
    private SpeechAudioLoader audio;
    @Mock
    private AzureSpeechAssessor assessor;
    private ShortQuestionScorer scorer;
    private final List<AudioChunk> chunks = oneChunk();

    @BeforeEach
    void setUp() {
        scorer = new ShortQuestionScorer(audio, assessor, JSON);
    }

    @Test
    void scoresAnswerShortQuestionTasksOnly() {
        assertThat(scorer.taskTypes()).containsExactly(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION);
    }

    @Test
    void theCanonicalAnswerScoresOneHundredAndSavesTheTranscript() {
        givenAudioHeardAs("Astronomer.");

        AiScoreResult result = scorer.score(request(ANSWER_KEY));

        assertThat(result.rawScore()).isEqualTo(100);
        assertThat(result.subScores()).containsOnly(java.util.Map.entry("CONTENT", 100));
        assertThat(result.transcript()).isEqualTo("Astronomer.");
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        JsonNode detail = JSON.readTree(result.detailJson());
        assertThat(detail.path("matchedAnswer").asText()).isEqualTo("astronomer");
        assertThat(detail.path("scoreLabel").asText()).isEqualTo("UNVALIDATED");
    }

    @Test
    void anAliasAlsoScoresOneHundredAndNamesTheAliasThatMatched() {
        givenAudioHeardAs("An astronomer!");

        AiScoreResult result = scorer.score(request(ANSWER_KEY));

        assertThat(result.rawScore()).isEqualTo(100);
        assertThat(JSON.readTree(result.detailJson()).path("matchedAnswer").asText()).isEqualTo("an astronomer");
    }

    @Test
    void theSpokenWordsMatchEvenWhenTheDisplayTextTurnedThemIntoDigits() {
        SpeechSegmentResult segment = new SpeechSegmentResult(true, "7.", "seven", 0, 50_000_000L, null, List.of());
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.transcribe(chunks))
                .thenReturn(new SpeechAssessment(null, null, null, List.of(), List.of(segment), 5_000));

        AiScoreResult result = scorer.score(request("seven"));

        assertThat(result.rawScore()).isEqualTo(100);
        assertThat(result.transcript()).isEqualTo("7.");
    }

    @Test
    void aWrongAnswerScoresZeroButKeepsTheTranscript() {
        givenAudioHeardAs("A biologist.");

        AiScoreResult result = scorer.score(request(ANSWER_KEY));

        assertThat(result.rawScore()).isZero();
        assertThat(result.transcript()).isEqualTo("A biologist.");
        assertThat(JSON.readTree(result.detailJson()).path("matchedAnswer").isNull()).isTrue();
    }

    @Test
    void silenceIsAWrongAnswerNotAFailure() {
        givenAudioHeardAs("");

        assertThat(scorer.score(request(ANSWER_KEY)).rawScore()).isZero();
    }

    @Test
    void onlyRecognitionRunsNeverThePronunciationPass() {
        givenAudioHeardAs("Astronomer.");

        scorer.score(request(ANSWER_KEY));

        verify(assessor, never()).assess(any(), any());
        verify(assessor, never()).assessScripted(any(), any());
    }

    @Test
    void anAnswerKeyWithoutWordsIsRejectedBeforeAnyAudioIsLoaded() {
        assertThatThrownBy(() -> scorer.score(request(" / ")))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getMessage()).isEqualTo(ScoringConstants.ANSWER_KEY_EMPTY));
        verify(audio, never()).load(any(), any());
    }

    private void givenAudioHeardAs(String transcript) {
        when(audio.load(MEDIA, TENANT)).thenReturn(new byte[] {1});
        when(assessor.prepare(any())).thenReturn(chunks);
        when(assessor.transcribe(chunks)).thenReturn(heard(transcript));
    }

    private static AiScoringRequest request(String correctAnswerText) {
        return new AiScoringRequest(ScoringConstants.TASK_TYPE_ANSWER_SHORT_QUESTION, MEDIA, null, null,
                correctAnswerText, null, null, null, TENANT);
    }
}
