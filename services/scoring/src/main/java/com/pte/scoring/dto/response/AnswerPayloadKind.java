package com.pte.scoring.dto.response;

/**
 * What shape a decoded answer payload takes, driven by data actually present
 * on {@code ScoringAnswer} (task type + whether {@code optionsJson} is
 * populated) rather than a hardcoded switch over all 23 PTE task types —
 * see {@code AnswerPayloadDecoder}'s class doc for the full rationale. The
 * five remaining Listening types ({@code SUMMARIZE_SPOKEN_TEXT}, {@code
 * MC_LISTENING_MULTIPLE}, {@code HIGHLIGHT_CORRECT_SUMMARY}, {@code
 * SELECT_MISSING_WORD}, and {@code WRITE_FROM_DICTATION}) still use the
 * generic text/selection fallback until their end-to-end payloads are
 * verified; {@code FILL_BLANKS_LISTENING} and {@code
 * HIGHLIGHT_INCORRECT_WORDS} are verified structured shapes.
 */
public enum AnswerPayloadKind {
    /** Student's spoken response — {@code mediaPublicId} is set, presigned separately by the caller. */
    AUDIO,
    /** Free-form text (essay, dictation, summarize-spoken-text, or any type with no options). */
    TEXT,
    /** One or more options picked from {@code optionsJson} — {@code options} is set. */
    SELECTION,
    /** Typed gap values in gap-index order — {@code gapValues} is set. */
    POSITIONAL_SELECTION,
    /** Transcript token positions selected by the student — {@code wordIndices} is set. */
    WORD_INDICES,
    /**
     * Payload did not match a known shape or was malformed. {@code text} carries
     * the raw, undecoded payload so the host still sees something rather than
     * nothing.
     */
    UNRECOGNIZED
}
