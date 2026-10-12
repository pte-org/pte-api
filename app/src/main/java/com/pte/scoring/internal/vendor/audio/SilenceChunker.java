package com.pte.scoring.internal.vendor.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a recording into chunks Azure short-audio accepts (it stops
 * listening after ~30 s). Each cut lands between 18 and 28 s into the current
 * chunk: in the middle of the longest pause there, or at the quietest 20 ms
 * frame when the speaker never pauses. Silence is judged relative to the
 * recording's own levels, so background noise does not hide every pause.
 */
public final class SilenceChunker {

    private static final long MAX_CHUNK_MS = 28_000;
    private static final long MIN_CUT_MS = 18_000;
    private static final int FRAME_MS = 20;
    private static final int MIN_PAUSE_FRAMES = 10;
    private static final double NOISE_PERCENTILE = 0.10;
    private static final double SPEECH_PERCENTILE = 0.90;
    private static final double SILENCE_THRESHOLD_RATIO = 0.10;
    // Digital silence (all-zero frames) is silent even when it fills the recording and the relative threshold is 0.
    private static final double MIN_SILENCE_THRESHOLD = 1.0;

    private SilenceChunker() {
    }

    public static List<AudioChunk> split(PcmAudio audio) {
        short[] samples = audio.samples();
        int rate = audio.sampleRate();
        int maxChunkSamples = (int) (MAX_CHUNK_MS * rate / 1000);
        if (samples.length <= maxChunkSamples) {
            return List.of(chunk(audio, 0, samples.length));
        }

        int frameSize = rate * FRAME_MS / 1000;
        double[] rms = frameRms(samples, frameSize);
        boolean[] silent = silentFrames(rms);
        int minCutSamples = (int) (MIN_CUT_MS * rate / 1000);

        List<AudioChunk> chunks = new ArrayList<>();
        int start = 0;
        while (samples.length - start > maxChunkSamples) {
            int cut = findCut(rms, silent, frameSize, start + minCutSamples, start + maxChunkSamples);
            chunks.add(chunk(audio, start, cut));
            start = cut;
        }
        if (!allSilent(silent, frameSize, start, samples.length)) {
            chunks.add(chunk(audio, start, samples.length));
        }
        return chunks;
    }

    private static int findCut(double[] rms, boolean[] silent, int frameSize, int windowStart, int windowEnd) {
        // Only frames lying entirely inside the window, so a cut never exceeds the limit.
        int firstFrame = Math.ceilDiv(windowStart, frameSize);
        int endFrame = windowEnd / frameSize;

        int bestStart = -1;
        int bestLength = 0;
        int runStart = -1;
        for (int frame = firstFrame; frame <= endFrame; frame++) {
            if (frame < endFrame && silent[frame]) {
                if (runStart < 0) {
                    runStart = frame;
                }
            } else if (runStart >= 0) {
                if (frame - runStart > bestLength) {
                    bestLength = frame - runStart;
                    bestStart = runStart;
                }
                runStart = -1;
            }
        }
        if (bestLength >= MIN_PAUSE_FRAMES) {
            return bestStart * frameSize + bestLength * frameSize / 2;
        }

        int quietest = firstFrame;
        for (int frame = firstFrame + 1; frame < endFrame; frame++) {
            if (rms[frame] < rms[quietest]) {
                quietest = frame;
            }
        }
        return quietest * frameSize + frameSize / 2;
    }

    private static double[] frameRms(short[] samples, int frameSize) {
        double[] rms = new double[Math.ceilDiv(samples.length, frameSize)];
        for (int frame = 0; frame < rms.length; frame++) {
            int from = frame * frameSize;
            int to = Math.min(samples.length, from + frameSize);
            double sum = 0;
            for (int i = from; i < to; i++) {
                sum += (double) samples[i] * samples[i];
            }
            rms[frame] = Math.sqrt(sum / (to - from));
        }
        return rms;
    }

    private static boolean[] silentFrames(double[] rms) {
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double noiseFloor = sorted[(int) ((sorted.length - 1) * NOISE_PERCENTILE)];
        double speechLevel = sorted[(int) ((sorted.length - 1) * SPEECH_PERCENTILE)];
        double threshold = Math.max(MIN_SILENCE_THRESHOLD,
                noiseFloor + SILENCE_THRESHOLD_RATIO * (speechLevel - noiseFloor));
        boolean[] silent = new boolean[rms.length];
        for (int frame = 0; frame < rms.length; frame++) {
            silent[frame] = rms[frame] < threshold;
        }
        return silent;
    }

    private static boolean allSilent(boolean[] silent, int frameSize, int from, int to) {
        for (int frame = from / frameSize; frame < Math.ceilDiv(to, frameSize); frame++) {
            if (!silent[frame]) {
                return false;
            }
        }
        return true;
    }

    private static AudioChunk chunk(PcmAudio audio, int from, int to) {
        int rate = audio.sampleRate();
        return new AudioChunk(WavEncoder.encode(audio.samples(), from, to, rate),
                from * 1000L / rate, (to - from) * 1000L / rate);
    }
}
