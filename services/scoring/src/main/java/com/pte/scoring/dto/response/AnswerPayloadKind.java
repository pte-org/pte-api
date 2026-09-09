package com.pte.scoring.dto.response;

/**
 * What shape a decoded answer payload takes, driven by data actually present
 * on {@code ScoringAnswer} (task type + whether {@code optionsJson} is
 * populated) rather than a hardcoded switch over all 23 PTE task types —
 * see {@code AnswerPayloadDecoder}'s class doc for the full rationale.
 */
public enum AnswerPayloadKind {
    /** Student's spoken response — {@code mediaPublicId} is set, presigned separately by the caller. */
    AUDIO,
    /** Free-form text (essay, dictation, summarize-spoken-text, or any type with no options). */
    TEXT,
    /** One or more options picked from {@code optionsJson} — {@code options} is set. */
    SELECTION,
    /**
     * Payload didn't match any known shape (malformed, or one of the 7 Listening
     * task types whose encoding has never been implemented/verified end-to-end —
     * see plan.md Risks). {@code text} carries the raw, undecoded payload so the
     * host still sees something rather than nothing.
     */
    UNRECOGNIZED
}
