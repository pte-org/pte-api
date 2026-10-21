package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.audio.AudioNormalizer;
import com.pte.scoring.internal.vendor.audio.SilenceChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Scores a whole recording through Azure short-audio: normalize to 16 kHz
 * mono, split at pauses into chunks of at most 28 s, call Azure once per
 * chunk in order, and stitch the results. Chunks run sequentially so one
 * answer never bursts the rate limit; the first failure aborts the answer.
 */
public class AzureSpeechAssessor {

    private final AzureSpeechRestClient client;

    public AzureSpeechAssessor(AzureSpeechRestClient client) {
        this.client = client;
    }

    /**
     * Decodes and splits once, so the caller can drop the raw recording before
     * the network calls and a two-pass answer reuses the same chunks.
     */
    public List<AudioChunk> prepare(byte[] audio) {
        return SilenceChunker.split(AudioNormalizer.normalize(audio));
    }

    /** Every chunk is assessed against the full reference; empty means unscripted. */
    public SpeechAssessment assess(List<AudioChunk> chunks, String referenceText) {
        return run(chunks, true, wav -> client.assess(wav, referenceText));
    }

    public SpeechAssessment transcribe(List<AudioChunk> chunks) {
        return run(chunks, false, client::recognize);
    }

    /**
     * Two passes for a read-out reference: recognition first, then each chunk is
     * assessed against only the reference words it was heard saying. A chunk
     * with no recognized speech, or whose words match none of the reference, is
     * not sent again; it scores zero (an empty reference would be assessed
     * unscripted and rate off-script speech highly).
     */
    public ScriptedAssessment assessScripted(List<AudioChunk> chunks, String referenceText) {
        SpeechAssessment heard = transcribe(chunks);
        List<String> parts = ReferenceSplitter.split(
                heard.segments().stream().map(SpeechSegmentResult::displayText).toList(), referenceText);
        List<SpeechSegmentResult> results = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            AudioChunk chunk = chunks.get(i);
            boolean scorable = heard.segments().get(i).speechDetected() && !parts.get(i).isBlank();
            results.add(scorable ? client.assess(chunk.wav(), parts.get(i)) : SpeechSegmentResult.noSpeech(true));
        }
        return new ScriptedAssessment(heard, SpeechAggregator.aggregate(chunks, results));
    }

    private static SpeechAssessment run(List<AudioChunk> chunks, boolean assessed,
                                        Function<byte[], SpeechSegmentResult> call) {
        List<SpeechSegmentResult> results = new ArrayList<>(chunks.size());
        for (AudioChunk chunk : chunks) {
            // An empty recording is a valid zero score; Azure would only reject it.
            results.add(chunk.durationMs() == 0 ? SpeechSegmentResult.noSpeech(assessed) : call.apply(chunk.wav()));
        }
        return SpeechAggregator.aggregate(chunks, results);
    }
}
