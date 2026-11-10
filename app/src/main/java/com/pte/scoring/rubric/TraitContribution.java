package com.pte.scoring.rubric;

import java.math.BigDecimal;

/**
 * One trait's share of an item score: the points achieved out of its maximum and the weight actually applied
 * (the parameter weight, multiplied by the word count for a per-word trait).
 */
public record TraitContribution(String key, int achieved, int max, BigDecimal effectiveWeight,
                                BigDecimal weightedContribution) {
}
