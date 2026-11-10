package com.pte.scoring.internal.constant;

/** Wording of the scoring-prompt documents shown on the AI admin pages (the web page localizes by section id). */
public final class ScoringPromptConstants {

    public static final String CATEGORY_SCORING = "SCORING";

    public static final String SECTION_ROLE_ID = "role";
    public static final String SECTION_ROLE_TITLE = "Role sent to the AI";
    public static final String SECTION_LEVELS_ID = "levels";
    public static final String SECTION_LEVELS_TITLE = "Level descriptions sent to the AI";
    public static final String SECTION_NOTES_ID = "notes";
    public static final String SECTION_NOTES_TITLE = "What the AI is told not to score";
    public static final String SECTION_GUARD_ID = "guard";
    public static final String SECTION_GUARD_TITLE = "Protection against instructions in the answer";
    public static final String SECTION_REFERENCE_ID = "reference";
    public static final String SECTION_REFERENCE_TITLE = "Reference only, not sent to the AI";

    public static final String ENGINE_GEMINI = "GEMINI";
    public static final String ENGINE_AZURE = "AZURE";
    public static final String ENGINE_CODE = "CODE";


    private ScoringPromptConstants() {
    }
}
