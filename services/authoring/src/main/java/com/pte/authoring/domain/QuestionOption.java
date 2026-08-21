package com.pte.authoring.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An answer option for options-based task types (multiple choice, re-order,
 * select-missing-word, highlight-correct-summary). {@code correct} marks the
 * expected choice(s); {@code orderIndex} preserves authored order.
 * {@code blankIndex} is null for every task type except
 * {@code FILL_BLANKS_READING_WRITING}, where it groups this option under one
 * of the question's several independently-choosable blanks (each blank has
 * its own distinct option list, unlike the shared word bank used by
 * {@code FILL_BLANKS_READING}).
 */
@Entity
@Table(name = "question_options")
@Getter
@Setter
@NoArgsConstructor
public class QuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private int orderIndex;

    @Column
    private Integer blankIndex;
}
