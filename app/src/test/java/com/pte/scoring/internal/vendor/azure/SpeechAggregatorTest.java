package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.vendor.audio.AudioChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SpeechAggregatorTest {

    private static final AudioChunk FIRST = new AudioChunk(new byte[0], 0, 20_000);
    private static final AudioChunk SECOND = new AudioChunk(new byte[0], 20_000, 10_000);

    private static final SpeechSegmentResult HELLO = new SpeechSegmentResult(true, "Hello there.", "hello there",
            0, 15_000_000, new PronunciationScores(90.0, 80.0, 100.0, 70.0, 85.0),
            List.of(new SpeechWord("hello", 1_000_000, 4_000_000, 100.0, "None"),
                    new SpeechWord("big", 5_000_000, 0, 0.0, "Omission"),
                    new SpeechWord("there", 6_000_000, 4_000_000, 90.0, "None")));

    private static final SpeechSegmentResult GENERAL = new SpeechSegmentResult(true, "General Kenobi, sir.",
            "general kenobi sir", 0, 9_000_000, new PronunciationScores(30.0, 50.0, 100.0, 40.0, 45.0),
            List.of(new SpeechWord("general", 500_000, 3_000_000, 60.0, "None"),
                    new SpeechWord("kenobi", 3_500_000, 3_000_000, 40.0, "Mispronunciation"),
                    new SpeechWord("sir", 7_000_000, 1_000_000, 20.0, "None")));

    @Test
    void joinsTranscriptsAndShiftsWordOffsetsByTheChunkOffset() {
        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND), List.of(HELLO, GENERAL));

        assertThat(result.displayText()).isEqualTo("Hello there. General Kenobi, sir.");
        assertThat(result.lexicalText()).isEqualTo("hello there general kenobi sir");
        assertThat(result.words()).extracting(SpeechWord::word)
                .containsExactly("hello", "big", "there", "general", "kenobi", "sir");
        assertThat(result.words().get(3).offsetTicks()).isEqualTo(200_500_000L);
        assertThat(result.words().get(3).durationTicks()).isEqualTo(3_000_000L);
        assertThat(result.segments()).containsExactly(HELLO, GENERAL);
        assertThat(result.audioDurationMs()).isEqualTo(30_000L);
        assertThat(result.speechDetected()).isTrue();
    }

    @Test
    void averagesAccuracyOverScoredWordsAndSkipsOmissions() {
        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND), List.of(HELLO, GENERAL));

        // (100 + 90 + 60 + 40 + 20) / 5 — not the per-chunk mean of 67.5.
        assertThat(result.accuracy()).isCloseTo(62.0, within(1e-9));
    }

    @Test
    void weightsFluencyAndProsodyByChunkDuration() {
        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND), List.of(HELLO, GENERAL));

        assertThat(result.fluency()).isCloseTo((80.0 * 20 + 50.0 * 10) / 30, within(1e-9));
        assertThat(result.prosody()).isCloseTo((70.0 * 20 + 40.0 * 10) / 30, within(1e-9));
    }

    @Test
    void aSilentChunkLowersFluencyButNotAccuracy() {
        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND),
                List.of(HELLO, SpeechSegmentResult.noSpeech(true)));

        assertThat(result.accuracy()).isCloseTo(95.0, within(1e-9));
        assertThat(result.fluency()).isCloseTo(80.0 * 20 / 30, within(1e-9));
        assertThat(result.displayText()).isEqualTo("Hello there.");
        assertThat(result.speechDetected()).isTrue();
    }

    @Test
    void noSpeechAnywhereIsAValidZeroScore() {
        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND),
                List.of(SpeechSegmentResult.noSpeech(true), SpeechSegmentResult.noSpeech(true)));

        assertThat(result.speechDetected()).isFalse();
        assertThat(result.displayText()).isEmpty();
        assertThat(result.accuracy()).isZero();
        assertThat(result.fluency()).isZero();
        assertThat(result.prosody()).isZero();
    }

    @Test
    void recognitionOnlyResultsHaveATranscriptButNoScores() {
        SpeechSegmentResult heard = new SpeechSegmentResult(true, "Paris.", "paris", 0, 5_000_000, null, List.of());

        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST), List.of(heard));

        assertThat(result.displayText()).isEqualTo("Paris.");
        assertThat(result.accuracy()).isNull();
        assertThat(result.fluency()).isNull();
        assertThat(result.prosody()).isNull();
    }

    @Test
    void skipsChunksWithoutAProsodyScore() {
        SpeechSegmentResult noProsody = new SpeechSegmentResult(true, "General.", "general", 0, 9_000_000,
                new PronunciationScores(30.0, 50.0, 100.0, null, 45.0), List.of());

        SpeechAssessment result = SpeechAggregator.aggregate(List.of(FIRST, SECOND), List.of(HELLO, noProsody));

        assertThat(result.prosody()).isCloseTo(70.0, within(1e-9));
    }

    @Test
    void rejectsMismatchedChunkAndResultCounts() {
        assertThatThrownBy(() -> SpeechAggregator.aggregate(List.of(FIRST, SECOND), List.of(HELLO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
