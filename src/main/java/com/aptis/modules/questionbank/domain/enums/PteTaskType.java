package com.aptis.modules.questionbank.domain.enums;

/**
 * The 20 official PTE Academic scored item types, plus PERSONAL_INTRODUCTION
 * (unscored warm-up recording — not one of the 20, tracked separately).
 * Verified against Pearson's public PTE Academic item-type list (Phase 1, Step 1a).
 */
public enum PteTaskType {

    PERSONAL_INTRODUCTION, // unscored

    // Speaking (5)
    READ_ALOUD,
    REPEAT_SENTENCE,
    DESCRIBE_IMAGE,
    RETELL_LECTURE,
    ANSWER_SHORT_QUESTION,

    // Writing (2)
    SUMMARIZE_WRITTEN_TEXT,
    WRITE_ESSAY,

    // Reading (5)
    READING_FILL_IN_THE_BLANKS,
    READING_WRITING_FILL_IN_THE_BLANKS,
    MULTIPLE_CHOICE_READING_SINGLE_ANSWER,
    MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER,
    RE_ORDER_PARAGRAPHS,

    // Listening (8)
    SUMMARIZE_SPOKEN_TEXT,
    MULTIPLE_CHOICE_LISTENING_SINGLE_ANSWER,
    MULTIPLE_CHOICE_LISTENING_MULTIPLE_ANSWER,
    LISTENING_FILL_IN_THE_BLANKS,
    HIGHLIGHT_CORRECT_SUMMARY,
    SELECT_MISSING_WORD,
    HIGHLIGHT_INCORRECT_WORDS,
    WRITE_FROM_DICTATION
}
