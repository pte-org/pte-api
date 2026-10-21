package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.vendor.audio.AudioChunk;

import java.util.List;

/** Builds the Azure results the scorers consume, without any HTTP. */
final class SpeechFixtures {

    private SpeechFixtures() {
    }

    static List<AudioChunk> oneChunk() {
        return List.of(new AudioChunk(new byte[] {1}, 0, 5_000));
    }

    /** What recognition alone heard: a transcript and no scores. */
    static SpeechAssessment heard(String displayText) {
        boolean speech = !displayText.isBlank();
        SpeechSegmentResult segment = new SpeechSegmentResult(speech, displayText, displayText.toLowerCase(),
                0, 50_000_000L, null, List.of());
        return new SpeechAssessment(null, null, null, List.of(), List.of(segment), 5_000);
    }

    /** What the assessment pass returned, with the given words (word, accuracy, error type). */
    static SpeechAssessment assessed(Double accuracy, Double fluency, Double prosody, SpeechWord... words) {
        PronunciationScores scores = new PronunciationScores(accuracy, fluency, 100.0, prosody, 90.0);
        SpeechSegmentResult segment = new SpeechSegmentResult(true, "ignored", "ignored", 0, 50_000_000L, scores,
                List.of(words));
        return new SpeechAssessment(accuracy, fluency, prosody, List.of(words), List.of(segment), 5_000);
    }

    static SpeechAssessment silence() {
        SpeechSegmentResult segment = SpeechSegmentResult.noSpeech(true);
        return new SpeechAssessment(0.0, 0.0, 0.0, List.of(), List.of(segment), 5_000);
    }

    static SpeechWord word(String word, double accuracy, String errorType) {
        return new SpeechWord(word, 0, 1_000_000, accuracy, errorType);
    }
}
