package com.pte.media.dto.response;

/**
 * {@code durationSeconds} is populated only for an audio-prompt object with a
 * successfully-extracted WAV duration — {@code null} for every other media
 * object. Public (not {@code internal/dto}) because {@link com.pte.media.MediaService}
 * returns it to cross-module trusted callers (attempt, scoring).
 */
public record PresignedDownloadResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
