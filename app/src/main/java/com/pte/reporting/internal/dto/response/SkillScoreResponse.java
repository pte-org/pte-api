package com.pte.reporting.internal.dto.response;

import java.math.BigDecimal;

public record SkillScoreResponse(String skill, Integer score, boolean sufficientData, BigDecimal appliedWeight) {

    /** Compatibility constructor for clients that only provide the score fields. */
    public SkillScoreResponse(String skill, Integer score, boolean sufficientData) {
        this(skill, score, sufficientData, null);
    }
}
