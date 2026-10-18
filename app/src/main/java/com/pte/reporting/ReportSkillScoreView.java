package com.pte.reporting;

import java.math.BigDecimal;

/** Public reporting read model for cross-module student workspace projections. */
public record ReportSkillScoreView(String skill, Integer score, boolean sufficientData, BigDecimal appliedWeight) {

    /** Compatibility constructor for callers that only need the score fields. */
    public ReportSkillScoreView(String skill, Integer score, boolean sufficientData) {
        this(skill, score, sufficientData, null);
    }
}
