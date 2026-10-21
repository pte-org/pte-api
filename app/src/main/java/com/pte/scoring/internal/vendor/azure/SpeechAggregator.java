package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.vendor.audio.AudioChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Stitches per-chunk Azure results into one assessment. Accuracy is averaged
 * per word so a short chunk counts for its few words, not as much as a long
 * one; fluency and prosody are per-chunk judgements, so they are weighted by
 * chunk duration and a silent chunk drags them down.
 */
public final class SpeechAggregator {

    private static final long TICKS_PER_MS = 10_000;
    // Words the speaker never said (or added) have no pronunciation to judge.
    private static final Set<String> UNSCORED_ERROR_TYPES = Set.of("Omission", "Insertion");

    private SpeechAggregator() {
    }

    public static SpeechAssessment aggregate(List<AudioChunk> chunks, List<SpeechSegmentResult> results) {
        if (chunks.size() != results.size()) {
            throw new IllegalArgumentException("Expected one result per audio chunk");
        }
        List<SpeechWord> words = new ArrayList<>();
        long audioDurationMs = 0;
        for (int i = 0; i < chunks.size(); i++) {
            long shift = chunks.get(i).offsetMs() * TICKS_PER_MS;
            results.get(i).words().forEach(word -> words.add(word.shiftedBy(shift)));
            audioDurationMs += chunks.get(i).durationMs();
        }
        return new SpeechAssessment(
                accuracy(chunks, results, words),
                weightedByDuration(chunks, results, PronunciationScores::fluency),
                weightedByDuration(chunks, results, PronunciationScores::prosody),
                words,
                results,
                audioDurationMs);
    }

    private static Double accuracy(List<AudioChunk> chunks, List<SpeechSegmentResult> results,
                                   List<SpeechWord> words) {
        List<Double> scored = words.stream()
                .filter(word -> word.accuracy() != null && !UNSCORED_ERROR_TYPES.contains(word.errorType()))
                .map(SpeechWord::accuracy)
                .toList();
        if (!scored.isEmpty()) {
            return scored.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
        // No scored words (all silence, or word detail missing): fall back to the segment scores.
        return weightedByDuration(chunks, results, PronunciationScores::accuracy);
    }

    private static Double weightedByDuration(List<AudioChunk> chunks, List<SpeechSegmentResult> results,
                                             Function<PronunciationScores, Double> score) {
        double total = 0;
        double weights = 0;
        for (int i = 0; i < results.size(); i++) {
            PronunciationScores scores = results.get(i).scores();
            Double value = scores == null ? null : score.apply(scores);
            if (value == null) {
                continue;
            }
            double weight = Math.max(1, chunks.get(i).durationMs());
            total += value * weight;
            weights += weight;
        }
        return weights == 0 ? null : total / weights;
    }
}
