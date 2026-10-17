package com.pte.reporting.dto.response;

import java.math.BigDecimal;

public record ReportSkillScoreView(String skill, Integer score, boolean sufficientData, BigDecimal appliedWeight) {

    /** Compatibility constructor for callers that only need the score fields. */
    public ReportSkillScoreView(String skill, Integer score, boolean sufficientData) {
        this(skill, score, sufficientData, null);
    }
}
