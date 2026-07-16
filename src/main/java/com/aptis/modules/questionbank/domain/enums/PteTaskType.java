package com.aptis.modules.questionbank.domain.enums;

/**
 * The 20 official PTE Academic scored item types, plus PERSONAL_INTRODUCTION
 * (unscored warm-up recording — not one of the 20, tracked separately).
 * Verified against Pearson's public PTE Academic item-type list (Phase 1, Step 1a).
 */
public enum PteTaskType {

    PERSONAL_INTRODUCTION(ScoringMode.UNSCORED, false),

    // Speaking (5) — AI-scored (speech). Only Repeat Sentence/Re-tell Lecture/Answer Short
    // Question present a recorded prompt (audioAssetId); Read Aloud's prompt is text,
    // Describe Image's prompt is an image (assetIds), neither needs an audio prompt.
    READ_ALOUD(ScoringMode.AI_SCORED, false),
    REPEAT_SENTENCE(ScoringMode.AI_SCORED, true),
    DESCRIBE_IMAGE(ScoringMode.AI_SCORED, false),
    RETELL_LECTURE(ScoringMode.AI_SCORED, true),
    ANSWER_SHORT_QUESTION(ScoringMode.AI_SCORED, true),

    // Writing (2) — AI-scored (essay), text prompt, no audio.
    SUMMARIZE_WRITTEN_TEXT(ScoringMode.AI_SCORED, false),
    WRITE_ESSAY(ScoringMode.AI_SCORED, false),

    // Reading (5) — deterministic, requires correct_answer(s), text prompt, no audio.
    READING_FILL_IN_THE_BLANKS(ScoringMode.OBJECTIVE, false),
    READING_WRITING_FILL_IN_THE_BLANKS(ScoringMode.OBJECTIVE, false),
    MULTIPLE_CHOICE_READING_SINGLE_ANSWER(ScoringMode.OBJECTIVE, false),
    MULTIPLE_CHOICE_READING_MULTIPLE_ANSWER(ScoringMode.OBJECTIVE, false),
    RE_ORDER_PARAGRAPHS(ScoringMode.OBJECTIVE, false),

    // Listening (8) — every Listening task presents an audio clip as its prompt.
    SUMMARIZE_SPOKEN_TEXT(ScoringMode.AI_SCORED, true),
    MULTIPLE_CHOICE_LISTENING_SINGLE_ANSWER(ScoringMode.OBJECTIVE, true),
    MULTIPLE_CHOICE_LISTENING_MULTIPLE_ANSWER(ScoringMode.OBJECTIVE, true),
    LISTENING_FILL_IN_THE_BLANKS(ScoringMode.OBJECTIVE, true),
    HIGHLIGHT_CORRECT_SUMMARY(ScoringMode.OBJECTIVE, true),
    SELECT_MISSING_WORD(ScoringMode.OBJECTIVE, true),
    HIGHLIGHT_INCORRECT_WORDS(ScoringMode.OBJECTIVE, true),
    WRITE_FROM_DICTATION(ScoringMode.OBJECTIVE, true);

    /** How a task type is scored — drives whether correct_answer(s) is a mandatory field. */
    public enum ScoringMode {
        UNSCORED,
        AI_SCORED,
        OBJECTIVE
    }

    private final ScoringMode scoringMode;
    private final boolean requiresAudioPrompt;

    PteTaskType(ScoringMode scoringMode, boolean requiresAudioPrompt) {
        this.scoringMode = scoringMode;
        this.requiresAudioPrompt = requiresAudioPrompt;
    }

    public ScoringMode getScoringMode() {
        return scoringMode;
    }

    /** Phase 1 Design Constraint: correct_answer(s) is mandatory at save-time for these task types. */
    public boolean requiresCorrectAnswer() {
        return scoringMode == ScoringMode.OBJECTIVE;
    }

    /** Whether this task type's question presents an audio clip as its prompt (needs audioAssetId before publish). */
    public boolean requiresAudioPrompt() {
        return requiresAudioPrompt;
    }
}
