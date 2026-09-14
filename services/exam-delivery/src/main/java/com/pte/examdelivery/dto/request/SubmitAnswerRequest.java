package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code payload} contract by task type (opaque string, interpreted by scoring
 * — Phase 7/9): options-based types (e.g. {@code MC_READING_SINGLE}) — the
 * selected option's {@code orderIndex} as a decimal string ("2"); free-text
 * types (e.g. {@code WRITE_ESSAY}) — the raw response text; audio types (e.g.
 * {@code READ_ALOUD}) — the media service's {@code MediaObject.publicId} for
 * the already-uploaded (and completed) recording, NOT raw audio bytes — the
 * student uploads to media directly via its presigned URL first, then submits
 * this reference (ADR-003: binary never flows through the transactional API tier).
 *
 * <p>Reading task types (ninh-pte-reading-task-types plan, Phase 7) extend the
 * decimal-string convention above, never introducing JSON encoding:
 * <ul>
 *   <li>{@code MC_READING_MULTIPLE} — comma-joined selected {@code orderIndex}
 *       values, sorted ascending (e.g. {@code "0,2,3"}).</li>
 *   <li>{@code RE_ORDER_PARAGRAPHS} — comma-joined {@code orderIndex} values in
 *       the student's final chosen order; the sequence itself is the answer
 *       (e.g. {@code "2,0,3,1"}).</li>
 *   <li>{@code FILL_BLANKS_READING} / {@code FILL_BLANKS_READING_WRITING} —
 *       positional comma-join, one entry per gap in gap-index order, each the
 *       assigned option's {@code orderIndex}, with an EMPTY entry (including a
 *       required trailing one) for an unanswered gap — e.g. {@code "2,0,"} for
 *       a 3-gap task where the last gap is unanswered, never {@code "2,0"}. A
 *       naive parse must use an explicit-limit split (e.g. Java's
 *       {@code split(",", -1)}) to preserve that trailing empty entry — the
 *       default {@code split(",")} silently drops it and misaligns every gap
 *       index after the first empty one.</li>
 * </ul>
 *
 * <p>Listening task types use the following payload encodings (Listening payload
 * contract version 1):
 * <ul>
 *   <li>{@code MC_LISTENING_SINGLE}, {@code HIGHLIGHT_CORRECT_SUMMARY}, and
 *       {@code SELECT_MISSING_WORD} — exactly one selected option's
 *       {@code orderIndex} as a decimal string (for example {@code "2"}); the
 *       value is not comma-joined.</li>
 *   <li>{@code MC_LISTENING_MULTIPLE} — selected option {@code orderIndex}
 *       values, numerically sorted ascending and comma-joined (for example
 *       {@code "0,2,3"}), regardless of the order in which the student toggled
 *       the options.</li>
 *   <li>{@code FILL_BLANKS_LISTENING} — raw typed text values in gap-index
 *       order, comma-joined. An unanswered gap is an empty entry, including a
 *       required trailing empty entry (for example {@code "rapid,,forest,"}
 *       represents four gaps: filled, unanswered, filled, unanswered). A comma
 *       is reserved as the separator and is forbidden inside a v1 gap value;
 *       no escaping or alternate encoding is defined.</li>
 *   <li>{@code HIGHLIGHT_INCORRECT_WORDS} — selected transcript token positions,
 *       numerically sorted ascending and comma-joined (for example
 *       {@code "3,7,11"}). These are transcript word indices, not option
 *       {@code orderIndex} values.</li>
 *   <li>{@code SUMMARIZE_SPOKEN_TEXT} and {@code WRITE_FROM_DICTATION} — the
 *       raw free-form draft text.</li>
 * </ul>
 */
public record SubmitAnswerRequest(
        @NotNull(message = "Task reference is required") UUID pinnedItemPublicId,
        /**
         * Blank/null is a legitimate submission (client-side-exam-timer Phase 2,
         * FR-07): resubmitted when the client's local countdown hits zero with
         * nothing answered, replacing the removed server-side auto-expire. Not
         * {@code @NotBlank} — the answer-processing path already treats a blank
         * payload as a normal (if content-less) answer end-to-end.
         */
        String payload) {
}
