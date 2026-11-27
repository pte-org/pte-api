package com.pte.itembank.internal.generation.draft;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blank markers in a passage. The exam client splits a passage on {{n}} markers, so every other way a model may mark
 * a blank (a run of underscores, __BLANK__, [BLANK], a bare {{}}) is rewritten to {{0}}, {{1}}, ... in reading order.
 */
public final class GapMarkers {

    private static final Pattern ANY_MARKER = Pattern.compile(
            "__BLANK__|\\[\\s*BLANK\\s*\\]|\\{\\{\\s*\\d*\\s*\\}\\}|_{3,}", Pattern.CASE_INSENSITIVE);

    /** The task types whose passage is split on {{n}} markers. */
    private static final Set<String> BLANK_TASKS = Set.of("FILL_IN_THE_BLANKS_DROPDOWN",
            "FILL_IN_THE_BLANKS_DRAG_AND_DROP", "FILL_IN_THE_BLANKS_TYPE_IN");

    private GapMarkers() {
    }

    /**
     * The prompt of a question being stored. Every way in is covered by this one rule (a generated item, a manual
     * entry, an import), so a fill-in-the-blanks passage never reaches a student with a marker they cannot drop into.
     * Other task types keep their prompt as written.
     */
    public static String forTask(String taskTypeKey, String prompt) {
        return taskTypeKey != null && BLANK_TASKS.contains(taskTypeKey) ? normalize(prompt) : prompt;
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
