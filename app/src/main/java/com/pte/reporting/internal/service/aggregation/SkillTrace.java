package com.pte.reporting.internal.service.aggregation;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a skill's (or Overall's) 10-90 score was reached: the weighted terms, {@code weightedSum} = Σ(w × avg / 100),
 * {@code totalWeight} = Σw of the included terms, {@code ratio} = weightedSum / totalWeight, {@code scaledScore} =
 * 10 + 80 × ratio before rounding and {@code score} rounded half-up. With Σw = 0 the result is insufficient data:
 * ratio, scaledScore and score are null.
 */
public record SkillTrace(List<SkillTermStep> terms, BigDecimal weightedSum, BigDecimal totalWeight, BigDecimal ratio,
                         BigDecimal scaledScore, Integer score, boolean sufficientData) {

    public SkillTrace {
        terms = terms == null ? List.of() : List.copyOf(terms);
    }
}
