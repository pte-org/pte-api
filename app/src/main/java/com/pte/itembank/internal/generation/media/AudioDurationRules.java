package com.pte.itembank.internal.generation.media;

import java.util.Map;
import java.util.Optional;

/** The spoken length, in seconds, a generated audio prompt must have for each task type. */
public final class AudioDurationRules {

    /** An inclusive range of seconds. */
    public record Range(double min, double max) {

        public boolean contains(double seconds) {
            return seconds >= min && seconds <= max;
        }

        public boolean isTooShort(double seconds) {
            return seconds < min;
        }
    }

    private static final Range SHORT_SENTENCE = new Range(3, 9);
    private static final Range LECTURE = new Range(50, 95);
    private static final Range PASSAGE = new Range(25, 75);

    private static final Map<String, Range> RULES = Map.ofEntries(
            Map.entry("WRITE_FROM_DICTATION", new Range(3, 8)),
            Map.entry("REPEAT_SENTENCE", SHORT_SENTENCE),
            Map.entry("ANSWER_SHORT_QUESTION", new Range(2, 8)),
            Map.entry("SUMMARIZE_SPOKEN_TEXT", LECTURE),
            Map.entry("RE_TELL_LECTURE", LECTURE),
            Map.entry("MC_LISTENING_SINGLE", PASSAGE),
            Map.entry("MC_LISTENING_MULTIPLE", PASSAGE),
            Map.entry("HIGHLIGHT_CORRECT_SUMMARY", PASSAGE),
            Map.entry("SELECT_MISSING_WORD", PASSAGE),
            Map.entry("HIGHLIGHT_INCORRECT_WORDS", PASSAGE),
            Map.entry("FILL_IN_THE_BLANKS_TYPE_IN", PASSAGE),
            Map.entry("SUMMARIZE_GROUP_DISCUSSION", new Range(70, 150)),
            Map.entry("RESPOND_TO_A_SITUATION", new Range(15, 45)));

    private AudioDurationRules() {
    }

    public static Optional<Range> forTask(String taskTypeKey) {
        return Optional.ofNullable(RULES.get(taskTypeKey));
    }

    /** Dictation and repeat-sentence are read slightly slower, as in the exam. */
    public static double baseSpeed(String taskTypeKey) {
        return "WRITE_FROM_DICTATION".equals(taskTypeKey) || "REPEAT_SENTENCE".equals(taskTypeKey) ? 0.95 : 1.0;
    }
}
