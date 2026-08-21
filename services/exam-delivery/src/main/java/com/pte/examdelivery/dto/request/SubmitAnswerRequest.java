package com.pte.examdelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
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
 */
public record SubmitAnswerRequest(
        @NotNull(message = "Task reference is required") UUID pinnedItemPublicId,
        @NotBlank(message = "Answer payload is required") String payload) {
}
