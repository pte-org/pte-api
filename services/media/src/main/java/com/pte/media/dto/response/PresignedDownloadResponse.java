package com.pte.media.dto.response;

/**
 * {@code durationSeconds} is populated only for an audio-prompt object with a
 * successfully-extracted WAV duration — {@code null} for every other media
 * object (plans/phat-speaking-dynamic-prep-timing). Reusing this one existing
 * response for both URL resolution and duration avoids a second internal
 * call/endpoint for the same underlying {@code MediaObject} row.
 */
public record PresignedDownloadResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
