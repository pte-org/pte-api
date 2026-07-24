package com.pte.authoring.domain.enums;

/**
 * The PTE skill set a task can contribute to: 4 communicative + 6 enabling skills
 * (reported on the 10–90 scale). A single task typically contributes to several
 * (config-driven mapping — see {@code PteTaskTypeSkillMapping}).
 */
public enum Skill {
    // Communicative
    LISTENING(true),
    READING(true),
    SPEAKING(true),
    WRITING(true),
    // Enabling
    GRAMMAR(false),
    ORAL_FLUENCY(false),
    PRONUNCIATION(false),
    SPELLING(false),
    VOCABULARY(false),
    WRITTEN_DISCOURSE(false);

    private final boolean communicative;

    Skill(boolean communicative) {
        this.communicative = communicative;
    }

    public boolean isCommunicative() {
        return communicative;
    }
}
