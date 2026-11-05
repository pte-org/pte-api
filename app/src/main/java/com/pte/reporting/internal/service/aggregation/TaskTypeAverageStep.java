package com.pte.reporting.internal.service.aggregation;

import java.math.BigDecimal;

/**
 * One task type's average raw score (FR-07): {@code scoredCount} answers with a score plus {@code blankCount} items the
 * student left blank (no answer row at all, counted as 0), {@code sum} of the scores and {@code average = sum /
 * (scoredCount + blankCount)} at DECIMAL64 precision. A task type with neither is not listed.
 */
public record TaskTypeAverageStep(String taskType, int scoredCount, int blankCount, BigDecimal sum,
                                  BigDecimal average) {
}
