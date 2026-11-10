package com.pte.itembank.internal.generation.draft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blank markers in a passage. The exam client splits a passage on {{n}} markers, so every other way a model may mark
 * a blank (a run of underscores, __BLANK__, [BLANK], a bare {{}}) is rewritten to {{0}}, {{1}}, ... in reading order.
 */
public final class GapMarkers {

    private static final Pattern ANY_MARKER = Pattern.compile(
            "__BLANK__|\\[\\s*BLANK\\s*\\]|\\{\\{\\s*\\d*\\s*\\}\\}|_{3,}", Pattern.CASE_INSENSITIVE);

    private GapMarkers() {
    }

    /** The passage with its blanks numbered {{0}}, {{1}}, ... in order. */
    public static String normalize(String passage) {
        if (passage == null) {
            return null;
        }
        Matcher matcher = ANY_MARKER.matcher(passage);
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("{{" + index + "}}"));
            index++;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** How many blanks the passage has. */
    public static int count(String passage) {
        if (passage == null) {
            return 0;
        }
        Matcher matcher = ANY_MARKER.matcher(passage);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
