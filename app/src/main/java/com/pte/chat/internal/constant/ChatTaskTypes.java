package com.pte.chat.internal.constant;

import java.util.Set;

/**
 * Task types accepted as chat context. The value is placed in the model prompt, so only these exact names
 * pass. Mirrors the itembank task types on purpose (chat may not depend on that module); a test compares them.
 */
public final class ChatTaskTypes {

    public static final Set<String> ALL = Set.of(
            "PERSONAL_INTRODUCTION", "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE",
            "ANSWER_SHORT_QUESTION", "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "MC_READING_SINGLE", "MC_READING_MULTIPLE",
            "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP", "FILL_IN_THE_BLANKS_DROPDOWN",
            "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");

    private ChatTaskTypes() {
    }

    public static boolean isAllowed(String value) {
        return value != null && ALL.contains(value);
    }
}
