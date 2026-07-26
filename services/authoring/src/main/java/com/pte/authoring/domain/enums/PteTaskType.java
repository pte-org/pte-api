package com.pte.authoring.domain.enums;

/**
 * The 22 scored PTE Academic task types (Pearson, as of the Aug 2025 update) plus
 * the unscored Personal Introduction (23 total). Each carries its authoring
 * input-requirements so validation is category-driven, not 22 hand-written rules.
 *
 * <p>Constructor flags: section, scored, then which authoring fields are REQUIRED:
 * audio prompt, image prompt, prompt text, answer options, correct answer(s),
 * response word-count bounds.
 */
public enum PteTaskType {

    // ---- Speaking ----
    PERSONAL_INTRODUCTION(PteSection.SPEAKING, false, false, false, true, false, false, false),
    READ_ALOUD(PteSection.SPEAKING, true, false, false, true, false, false, false),
    REPEAT_SENTENCE(PteSection.SPEAKING, true, true, false, false, false, false, false),
    DESCRIBE_IMAGE(PteSection.SPEAKING, true, false, true, false, false, false, false),
    RE_TELL_LECTURE(PteSection.SPEAKING, true, true, false, false, false, false, false),
    ANSWER_SHORT_QUESTION(PteSection.SPEAKING, true, true, false, false, false, true, false),
    RESPOND_TO_A_SITUATION(PteSection.SPEAKING, true, true, false, true, false, false, false),
    SUMMARIZE_GROUP_DISCUSSION(PteSection.SPEAKING, true, true, false, false, false, false, false),

    // ---- Writing ----
    SUMMARIZE_WRITTEN_TEXT(PteSection.WRITING, true, false, false, true, false, false, true),
    WRITE_ESSAY(PteSection.WRITING, true, false, false, true, false, false, true),

    // ---- Reading ----
    MC_READING_SINGLE(PteSection.READING, true, false, false, true, true, true, false),
    MC_READING_MULTIPLE(PteSection.READING, true, false, false, true, true, true, false),
    RE_ORDER_PARAGRAPHS(PteSection.READING, true, false, false, false, true, true, false),
    FILL_BLANKS_READING(PteSection.READING, true, false, false, true, true, true, false),
    FILL_BLANKS_READING_WRITING(PteSection.READING, true, false, false, true, true, true, false),

    // ---- Listening ----
    SUMMARIZE_SPOKEN_TEXT(PteSection.LISTENING, true, true, false, false, false, false, true),
    MC_LISTENING_SINGLE(PteSection.LISTENING, true, true, false, false, true, true, false),
    MC_LISTENING_MULTIPLE(PteSection.LISTENING, true, true, false, false, true, true, false),
    FILL_BLANKS_LISTENING(PteSection.LISTENING, true, true, false, true, false, true, false),
    HIGHLIGHT_CORRECT_SUMMARY(PteSection.LISTENING, true, true, false, false, true, true, false),
    SELECT_MISSING_WORD(PteSection.LISTENING, true, true, false, false, true, true, false),
    HIGHLIGHT_INCORRECT_WORDS(PteSection.LISTENING, true, true, false, true, false, true, false),
    WRITE_FROM_DICTATION(PteSection.LISTENING, true, true, false, false, false, true, false);

    private final PteSection section;
    private final boolean scored;
    private final boolean requiresAudioPrompt;
    private final boolean requiresImagePrompt;
    private final boolean requiresPromptText;
    private final boolean requiresOptions;
    private final boolean requiresCorrectAnswer;
    private final boolean requiresWordCount;

    PteTaskType(PteSection section, boolean scored, boolean requiresAudioPrompt, boolean requiresImagePrompt,
                boolean requiresPromptText, boolean requiresOptions, boolean requiresCorrectAnswer,
                boolean requiresWordCount) {
        this.section = section;
        this.scored = scored;
        this.requiresAudioPrompt = requiresAudioPrompt;
        this.requiresImagePrompt = requiresImagePrompt;
        this.requiresPromptText = requiresPromptText;
        this.requiresOptions = requiresOptions;
        this.requiresCorrectAnswer = requiresCorrectAnswer;
        this.requiresWordCount = requiresWordCount;
    }

    public PteSection getSection() {
        return section;
    }

    public boolean isScored() {
        return scored;
    }

    public boolean requiresAudioPrompt() {
        return requiresAudioPrompt;
    }

    public boolean requiresImagePrompt() {
        return requiresImagePrompt;
    }

    public boolean requiresPromptText() {
        return requiresPromptText;
    }

    public boolean requiresOptions() {
        return requiresOptions;
    }

    public boolean requiresCorrectAnswer() {
        return requiresCorrectAnswer;
    }

    public boolean requiresWordCount() {
        return requiresWordCount;
    }
}
