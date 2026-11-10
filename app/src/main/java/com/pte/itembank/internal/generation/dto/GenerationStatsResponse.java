package com.pte.itembank.internal.generation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * How question generation went over the last {@code days} days. {@code reviewRejected} counts items whose draft the
 * reviewer refused after the one rewrite; it is the number to watch when deciding whether the reviewer is too strict.
 */
public record GenerationStatsResponse(int days, int saved, int failed, int reviewRejected,
                                      List<TaskTypeStats> byTaskType, List<FailureStats> failures,
                                      TokenStats tokens) {

    /** Items of one task type. */
    public record TaskTypeStats(String taskType, int saved, int failed) {
    }

    /**
     * Provider calls of generation and review in the period, the tokens they used (input, output) and their cost at
     * the configured prices; an estimate, like the chat cost.
     */
    public record TokenStats(long calls, long tokensIn, long tokensOut, BigDecimal estimatedCostUsd) {
    }

    /** Items that failed with one code. */
    public record FailureStats(String code, int count) {
    }
}
