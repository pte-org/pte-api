package com.pte.scoring.internal.vendor.azure;

/**
 * One recognized word. Offsets are Azure's 100-ns ticks; accuracy and error
 * type are null when the call was recognition-only.
 */
public record SpeechWord(String word, long offsetTicks, long durationTicks, Double accuracy, String errorType) {

    public SpeechWord shiftedBy(long ticks) {
        return new SpeechWord(word, offsetTicks + ticks, durationTicks, accuracy, errorType);
    }
}
