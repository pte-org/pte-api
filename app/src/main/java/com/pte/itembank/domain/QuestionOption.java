package com.pte.itembank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An answer option for options-based task types (multiple choice, re-order,
 * select-missing-word, highlight-correct-summary), stored in the
 * {@code question_options} child table of its {@link Question}. {@code correct} marks the
 * expected choice(s); {@code orderIndex} preserves authored order. {@code publicId} is the identifier
 * clients use to refer to the option; it is assigned when the option is authored and replaced together with
 * the whole option list on every edit.
 * {@code blankIndex} is null for every task type except
 * {@code FILL_IN_THE_BLANKS_DROPDOWN}, where it groups this option under one
 * of the question's several independently-choosable blanks (each blank has
 * its own distinct option list, unlike the shared word bank used by
 * {@code FILL_IN_THE_BLANKS_DRAG_AND_DROP}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class QuestionOption {

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "blank_index")
    private Integer blankIndex;

    /**
     * Scoring-only: for a {@code FILL_IN_THE_BLANKS_DRAG_AND_DROP} (shared word-bank)
     * correct option, the gap index this word is correct for. Null for
     * every distractor option and for every other task type — deliberately
     * distinct from {@link #blankIndex}: setting both on the same option
     * would make attempt's delivery-shape routing (which depends only on
     * {@code blankIndex}) treat a shared-word-bank task as blank-grouped, so
     * this field must never be set alongside a non-null {@code blankIndex}.
     */
    @Column(name = "correct_gap_index")
    private Integer correctGapIndex;
}
