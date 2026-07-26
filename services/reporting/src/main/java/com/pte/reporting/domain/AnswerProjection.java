package com.pte.reporting.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * reporting's projection of one answer, enough to aggregate scores per skill —
 * {@code taskType} for the skill-mapping lookup, {@code rawScore} once
 * {@code AnswerScored} arrives. Unscored rows (rawScore null) are exactly what
 * makes a skill report "insufficient data."
 */
@Entity
@Table(name = "answer_projections")
@Getter
@Setter
@NoArgsConstructor
public class AnswerProjection extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID answerPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private String taskType;

    @Column
    private Integer rawScore;

    @Column(nullable = false)
    private boolean scored = false;

    public void applyScore(int rawScore) {
        this.rawScore = rawScore;
        this.scored = true;
    }
}
