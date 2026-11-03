package com.pte.itembank.internal.service;

import java.util.Collection;

/** Typical stimulus length of a task type from the measured lengths of its questions. */
public final class StimulusLength {

    private static final int EVEN_COUNT_DIVISOR = 2;

    private StimulusLength() {
    }

    /** Median of the lengths rounded to whole seconds, or {@code fallbackSeconds} when there are none. */
    public static int median(Collection<Integer> lengthsSeconds, int fallbackSeconds) {
        if (lengthsSeconds.isEmpty()) {
            return fallbackSeconds;
        }
        int[] sorted = lengthsSeconds.stream().mapToInt(Integer::intValue).sorted().toArray();
        int middle = sorted.length / EVEN_COUNT_DIVISOR;
        if (sorted.length % EVEN_COUNT_DIVISOR == 1) {
            return sorted[middle];
        }
        return Math.round((sorted[middle - 1] + sorted[middle]) / (float) EVEN_COUNT_DIVISOR);
    }
}
