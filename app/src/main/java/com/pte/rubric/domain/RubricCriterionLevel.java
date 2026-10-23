package com.pte.rubric.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rubric_criterion_levels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rubric_level_key", columnNames = {"criterion_id", "level_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class RubricCriterionLevel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RubricCriterion criterion;

    @Column(name = "level_key", nullable = false, length = 32)
    private String levelKey;

    @Column(name = "level_order", nullable = false)
    private int levelOrder;

    @Column(name = "label_vi", nullable = false, length = 128)
    private String labelVi;

    @Column(name = "label_en", nullable = false, length = 128)
    private String labelEn;

    @Column(nullable = false, columnDefinition = "text")
    private String descriptor;
}
