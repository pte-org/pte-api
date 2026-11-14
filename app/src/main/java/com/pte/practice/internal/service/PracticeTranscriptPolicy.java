package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeResponseStatus;

import java.util.Set;

/**
 * Listening and speaking tasks test what the learner hears, so their transcript ({@code promptText}) is withheld from
 * the student until the item is answered; the audio is the only prompt. Showing it earlier would turn Repeat Sentence
 * into Read Aloud and Write From Dictation into copying.
 */
public final class PracticeTranscriptPolicy {

    private static final Set<String> AUDIO_ONLY_TASK_CODES = Set.of(
            "ANSWER_SHORT_QUESTION", "REPEAT_SENTENCE", "RE_TELL_LECTURE", "WRITE_FROM_DICTATION",
            "SUMMARIZE_SPOKEN_TEXT", "SELECT_MISSING_WORD", "HIGHLIGHT_CORRECT_SUMMARY");

    private PracticeTranscriptPolicy() {
    }

    /** True while the transcript of this item must not reach the student. */
    public static boolean withheld(PracticeResponse item) {
        return AUDIO_ONLY_TASK_CODES.contains(item.getTaskCode())
                && item.getStatus() == PracticeResponseStatus.PENDING;
    }

    /** The prompt text the student may see now: null while withheld. */
    public static String visiblePromptText(PracticeResponse item) {
        return withheld(item) ? null : item.getPromptText();
    }
}
