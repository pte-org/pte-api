package com.pte.rubric.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rubric_criteria", uniqueConstraints = {
        @UniqueConstraint(name = "uk_rubric_criterion_key", columnNames = {"revision_id", "criterion_id"})
}, indexes = {
        @Index(name = "idx_rubric_criterion_revision", columnList = "revision_id,sequence")
})
@Getter
@Setter
@NoArgsConstructor
public class RubricCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RubricRevision revision;

    @Column(name = "criterion_id", nullable = false, length = 64)
    private String criterionId;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "applicability", nullable = false, length = 32)
    private String applicability;

    @Column(name = "evidence_policy_json", nullable = false, columnDefinition = "text")
    private String evidencePolicyJson;

    @OneToMany(mappedBy = "criterion", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("levelOrder ASC")
    private List<RubricCriterionLevel> levels = new ArrayList<>();

    public void addLevel(RubricCriterionLevel level) {
        level.setCriterion(this);
        levels.add(level);
    }
}
