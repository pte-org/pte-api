package com.pte.scoring.internal.vendor.azure;

import java.util.List;

/**
 * Azure's answer for one audio chunk. {@code scores} is null for a
 * recognition-only call; a chunk with no speech scores zero when assessed.
 */
public record SpeechSegmentResult(boolean speechDetected, String displayText, String lexicalText,
                                  long offsetTicks, long durationTicks, PronunciationScores scores,
                                  List<SpeechWord> words) {

    public SpeechSegmentResult {
        displayText = displayText == null ? "" : displayText;
        lexicalText = lexicalText == null ? "" : lexicalText;
        words = words == null ? List.of() : List.copyOf(words);
    }

    public static SpeechSegmentResult noSpeech(boolean assessed) {
        return new SpeechSegmentResult(false, "", "", 0, 0, assessed ? PronunciationScores.zero() : null, List.of());
    }
}
