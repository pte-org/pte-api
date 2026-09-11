package com.pte.examdelivery.client.dto;

/**
 * exam-delivery's own view of media's presigned-download response — not a
 * shared class. {@code durationSeconds} is populated only for an audio-prompt
 * media object with a successfully-extracted WAV duration — {@code null} for
 * every other media object (plans/phat-speaking-dynamic-prep-timing).
 */
public record MediaPresignedDownloadResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
