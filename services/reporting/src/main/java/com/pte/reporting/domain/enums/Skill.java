package com.pte.reporting.domain.enums;

/**
 * reporting's own copy of the PTE skill set (mirrors authoring's {@code Skill}
 * enum by value — not a shared class across services). 4 communicative + 6
 * enabling skills, each reportable as a 10–90 scaled score or "insufficient data."
 */
public enum Skill {
    LISTENING(true),
    READING(true),
    SPEAKING(true),
    WRITING(true),
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
