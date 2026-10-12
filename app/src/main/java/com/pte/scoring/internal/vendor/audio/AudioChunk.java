package com.pte.scoring.internal.vendor.audio;

/**
 * One self-contained WAV slice of a recording, with where it sits in the
 * original so per-chunk results can be stitched back together.
 */
public record AudioChunk(byte[] wav, long offsetMs, long durationMs) {
}
