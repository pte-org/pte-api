package com.pte.reporting.internal.dto.response;

import java.math.BigDecimal;

/**
 * A weighted task type in a skill's sum: the template weight, the task type's average and {@code weight × average /
 * 100}; {@code excluded} task types have no average yet and are left out of both Σ(w × avg) and Σw.
 */
public record SkillTermResponse(String taskType, BigDecimal weight, BigDecimal average, BigDecimal contribution,
                                boolean excluded) {
}
