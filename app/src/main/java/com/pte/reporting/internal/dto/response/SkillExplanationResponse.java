package com.pte.reporting.internal.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * A skill's (or Overall's) score with its chain: {@code weightedSum} = Σ(w × avg / 100), {@code totalWeight} = Σw,
 * {@code ratio} = weightedSum / totalWeight, {@code scaledScore} = 10 + 80 × ratio before half-up rounding to
 * {@code score}. The chain fields are null and {@code terms} empty when no trace is available (schema-1 snapshot).
 */
public record SkillExplanationResponse(
        String skill,
        Integer score,
        boolean sufficientData,
        BigDecimal weightedSum,
        BigDecimal totalWeight,
        BigDecimal ratio,
        BigDecimal scaledScore,
        List<SkillTermResponse> terms) {
}
