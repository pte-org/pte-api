package com.pte.reporting.internal.dto.response;

import java.math.BigDecimal;

/** A task type's average: {@code sum / (scoredCount + blankCount)}; blanks are items left unanswered, counted as 0. */
public record TaskTypeAverageResponse(String taskType, int scoredCount, int blankCount, BigDecimal sum,
                                      BigDecimal average) {
}
