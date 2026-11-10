package com.pte.reporting.internal.service.aggregation;

import java.math.BigDecimal;

/**
 * One weighted task type in a skill's (or Overall's) sum: its weight from the pinned exam template, its average, and
 * {@code contribution = weight × average / 100}. {@code excluded} means the task type has no average yet (nothing
 * scored, no blank), so it is left out of both Σ(w × avg) and Σw; its average and contribution are then null.
 */
public record SkillTermStep(String taskType, BigDecimal weight, BigDecimal average, BigDecimal contribution,
                            boolean excluded) {
}
