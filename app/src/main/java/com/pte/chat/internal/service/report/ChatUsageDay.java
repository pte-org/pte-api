package com.pte.chat.internal.service.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Vietnam day of chat traffic. It carries counts and tokens only: nothing that names a student, a session
 * or what was asked, so it is safe to show to anyone who may see cost.
 */
public record ChatUsageDay(
        LocalDate date,
        int messages,
        int ok,
        int refused,
        int failed,
        int cacheHits,
        long tokensIn,
        long tokensOut,
        long tokensThinking,
        BigDecimal estimatedCostUsd) {
}
