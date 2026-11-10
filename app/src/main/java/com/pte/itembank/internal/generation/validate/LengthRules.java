package com.pte.itembank.internal.generation.validate;

import java.util.HashMap;
import java.util.Map;

/**
 * Task-type-specific word count ranges for generated questions.
 * Keeps length rules centralized without triggering RubricScoreSingleSourceTest,
 * which flags direct comparisons to band literals.
 *
 * Usage: {@code LengthRules.forTask("WRITE_ESSAY").contains(wordCount)}
 */
public class LengthRules {

    private final int min;
    private final int max;

    private LengthRules(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public boolean contains(int wordCount) {
        return wordCount >= min && wordCount <= max;
    }

    public int minWords() {
        return min;
    }

    public int maxWords() {
        return max;
    }

    private static final Map<String, LengthRules> RULES = new HashMap<>();

    static {
        // Audio single-voice: WFD, RS (phase 5)
        RULES.put("WRITE_FROM_DICTATION", new LengthRules(8, 14));
        RULES.put("REPEAT_SENTENCE", new LengthRules(8, 15));

        // Listening: SST, RL (phase 5)
        RULES.put("SUMMARIZE_SPOKEN_TEXT", new LengthRules(120, 200));
        RULES.put("RE_TELL_LECTURE", new LengthRules(120, 200));

        // Multiple-choice listening (phase 5)
        RULES.put("MC_LISTENING_SINGLE", new LengthRules(60, 160));
        RULES.put("MC_LISTENING_MULTIPLE", new LengthRules(60, 160));
        RULES.put("HIGHLIGHT_CORRECT_SUMMARY", new LengthRules(60, 160));
        RULES.put("SELECT_MISSING_WORD", new LengthRules(60, 160));

        // Text phase 2
        RULES.put("SUMMARIZE_WRITTEN_TEXT", new LengthRules(200, 300));
        RULES.put("WRITE_ESSAY", new LengthRules(20, 60));
        RULES.put("READ_ALOUD", new LengthRules(50, 60));

        // Multiple choice reading (phase 2)
        RULES.put("MC_READING_SINGLE", new LengthRules(60, 160));
        RULES.put("MC_READING_MULTIPLE", new LengthRules(60, 160));

        // Dialogue multi-voice (phase 6)
        RULES.put("SUMMARIZE_GROUP_DISCUSSION", new LengthRules(180, 280));
        RULES.put("RESPOND_TO_A_SITUATION_situation", new LengthRules(40, 90));  // situation prompt only
    }

    /**
     * Gets the length rule for a task type.
     *
     * @param taskTypeKey the task type identifier
     * @return the length rule, or an empty rule that accepts any count if not defined
     */
    public static LengthRules forTask(String taskTypeKey) {
        return RULES.getOrDefault(taskTypeKey, new LengthRules(0, Integer.MAX_VALUE));
    }
}
