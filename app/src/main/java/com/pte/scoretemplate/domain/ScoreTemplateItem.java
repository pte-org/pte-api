package com.pte.scoretemplate.domain;

import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.domain.enums.TimingMode;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One task-type row of a {@link ScoreTemplate} (one of the 22 scored PTE
 * task types). {@code taskType}/{@code section} are plain {@code String}
 * (not {@code itembank.PteTaskType}/{@code PteSection}) on purpose — this
 * module stays independent of {@code itembank} (Spring Modulith boundary),
 * mirroring how {@code attempt.PinnedItem}/{@code scoring.ScoringAnswer}
 * already carry task type as a String across module lines. Values must match
 * {@code PteTaskType}/{@code PteSection} enum names; callers that need the
 * real enum parse it themselves (e.g. {@code PteTaskType.valueOf(...)}).
 *
 * <p>A weight of {@code null} or {@code 0} means "does not contribute to
 * that skill" (FR-02). Weights are percentages on the same 0-100-ish scale
 * as the APEUni V5 table and are NOT required to sum to 100 per skill/column
 * (the source table itself doesn't, due to rounding) — see
 * {@code ScoreTemplateActivationValidator} for what IS enforced.
 */
@Entity
@Table(name = "score_template_items", indexes = {
        @Index(name = "idx_score_template_items_template", columnList = "template_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ScoreTemplateItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScoreTemplate template;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private int minCount;

    @Column(nullable = false)
    private int maxCount;

    @Column(nullable = false)
    private int prepSeconds;

    @Column(nullable = false)
    private int responseSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimingMode timingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoringMethod scoringMethod;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal overallWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal speakingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal writingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal readingWeight = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal listeningWeight = BigDecimal.ZERO;
}
