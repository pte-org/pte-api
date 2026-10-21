package com.pte.scoring.internal.vendor.audio;

/** Mono 16-bit signed PCM samples at {@code sampleRate}. */
public record PcmAudio(short[] samples, int sampleRate) {

    public long durationMs() {
        return samples.length * 1000L / sampleRate;
    }
}
