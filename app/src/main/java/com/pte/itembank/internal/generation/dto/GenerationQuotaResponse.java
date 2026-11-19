package com.pte.itembank.internal.generation.dto;

/**
 * The caller's use of today's allowance (UTC day), so the form can warn before submitting. {@code remaining} and
 * {@code scope} come from the tighter non-zero daily limit and are null when both daily limits are unlimited;
 * {@code dailyLimit} is the caller's own limit (0 = unlimited). {@code maxItemsPerBatch} is the batch cap in force.
 */
public record GenerationQuotaResponse(long usedToday, int dailyLimit, Integer remaining, boolean unlimited,
                                      QuotaScope scope, int maxItemsPerBatch) {
}
