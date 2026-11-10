package com.pte.itembank.internal.generation.dto;

/** The caller's use of today's allowance (UTC day), so the form can warn before submitting. */
public record GenerationQuotaResponse(long usedToday, int dailyLimit) {
}
