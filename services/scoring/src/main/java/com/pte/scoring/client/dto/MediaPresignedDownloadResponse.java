package com.pte.scoring.client.dto;

/**
 * scoring's own view of media's presigned-download response — not a shared
 * class (§3: no shared DTOs across services), mirrors exam-delivery's DTO of
 * the same shape. {@code durationSeconds} is populated only for an
 * audio-prompt media object with a successfully-extracted WAV duration;
 * unused by scoring today (no dynamic-prep-timing concern here), kept for
 * parity with the source contract.
 */
public record MediaPresignedDownloadResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
