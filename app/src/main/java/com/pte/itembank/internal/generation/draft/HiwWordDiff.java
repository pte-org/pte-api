package com.pte.itembank.internal.generation.draft;

import java.util.stream.IntStream;

/**
 * The positions of the changed words of a Highlight Incorrect Words draft, found by comparing the two texts word by
 * word. Models miscount word positions in a long passage, so the indexes they return are not used. When the word
 * counts differ there is no position-wise answer; the validator then reports the count mismatch.
 */
final class HiwWordDiff {

    private static final String WHITESPACE = "\s+";

    private HiwWordDiff() {
    }

    static int[] changedIndexes(String transcript, String displayedText) {
        String[] original = transcript.strip().split(WHITESPACE);
        String[] shown = displayedText.strip().split(WHITESPACE);
        if (original.length != shown.length) {
            return new int[0];
        }
        return IntStream.range(0, original.length)
                .filter(index -> !original[index].equalsIgnoreCase(shown[index])).toArray();
    }
}
