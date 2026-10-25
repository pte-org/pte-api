package com.pte.practice.internal.dto.response;

/**
 * Short-lived link to the student's own saved recording of an item, used to replay it and draw its waveform after
 * the page was reloaded or the session resumed.
 *
 * @param expiresInSeconds 0 for legacy public assets whose link does not expire
 */
public record PracticeRecordingPlaybackResponse(String url, long expiresInSeconds, Integer durationSeconds) {
}
