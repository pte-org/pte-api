package com.pte.scoring.internal.vendor.azure;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A whole recording's result, stitched from its chunks. Scores are null for
 * a recognition-only transcription. Completeness and the overall
 * pronunciation score are left to the scorer, which aligns words against
 * the reference itself. Word offsets are on the recording's timeline; segment
 * offsets stay relative to their own chunk.
 */
public record SpeechAssessment(Double accuracy, Double fluency, Double prosody, List<SpeechWord> words,
                               List<SpeechSegmentResult> segments, long audioDurationMs) {

    public SpeechAssessment {
        words = List.copyOf(words);
        segments = List.copyOf(segments);
    }

    public String displayText() {
        return join(SpeechSegmentResult::displayText);
    }

    public String lexicalText() {
        return join(SpeechSegmentResult::lexicalText);
    }

    public boolean speechDetected() {
        return segments.stream().anyMatch(SpeechSegmentResult::speechDetected);
    }

    private String join(Function<SpeechSegmentResult, String> text) {
        return segments.stream()
                .map(text.andThen(String::trim))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
