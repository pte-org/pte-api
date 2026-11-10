package com.pte.scoring.dto.response;

/**
 * How an answer's item score can be explained.
 * <ul>
 *   <li>{@code RUBRIC}: recomputed from the stored Pearson trait scores with the answer's pinned rubric version;</li>
 *   <li>{@code LEGACY_NO_TRACE}: an AI score stored before trait scores were kept on the Pearson scale;</li>
 *   <li>{@code EXAMINER_SCORE}: the examiner's overall score was chosen; it has no trait breakdown;</li>
 *   <li>{@code OBJECTIVE}: scored by an answer-key rule; the raw score is the rule's result;</li>
 *   <li>{@code UNSCORED}: a task type the exam does not score;</li>
 *   <li>{@code NOT_SCORED}: no score is available yet;</li>
 *   <li>{@code TRACE_UNAVAILABLE}: stored trait scores exist but cannot be recomputed with the pinned version.</li>
 * </ul>
 */
public enum ItemTraceKind {
    RUBRIC, LEGACY_NO_TRACE, EXAMINER_SCORE, OBJECTIVE, UNSCORED, NOT_SCORED, TRACE_UNAVAILABLE
}
