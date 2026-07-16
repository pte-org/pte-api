package com.aptis.modules.questionbank.domain.enums;

/**
 * Communicative skills (LISTENING, READING, SPEAKING, WRITING) and enabling
 * skills (GRAMMAR, ORAL_FLUENCY, PRONUNCIATION, SPELLING, VOCABULARY,
 * WRITTEN_DISCOURSE) reported on the PTE 10-90 score scale. A single
 * question no longer maps to exactly one skill — see
 * {@link PteTaskTypeSkillMapping} for the task-type -> skills mapping used
 * by score aggregation.
 */
public enum Skill {
    // Communicative skills (4)
    LISTENING,
    READING,
    SPEAKING,
    WRITING,

    // Enabling skills (6)
    GRAMMAR,
    ORAL_FLUENCY,
    PRONUNCIATION,
    SPELLING,
    VOCABULARY,
    WRITTEN_DISCOURSE
}
