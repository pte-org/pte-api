package com.pte.practice.internal.dto.response;

/**
 * Per-item pacing of a TIMED practice session, pinned from the active Score Template when the session began.
 *
 * <p>{@code preRecordSeconds} is the template's gap before recording only. It is not the exam's
 * {@code PinnedItem.prepSeconds}, which adds {@code preListenSeconds} and the prompt audio length; the practice
 * client waits for the audio to finish instead, so the three values are never summed on the server.</p>
 *
 * @param preListenSeconds seconds before the prompt audio plays, or null when the task has no such stage
 */
public record PracticeItemTimingResponse(Integer preListenSeconds, int preRecordSeconds, int responseSeconds) {
}
