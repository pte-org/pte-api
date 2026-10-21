package com.pte.reporting.internal.service;

/**
 * Pre-publication score summary. {@code includesUnselectedAiScores} is true when at least one answer with no
 * chosen score source was counted by its AI score, so the figures may change once the Host chooses.
 */
public record ProvisionalScoreSummary(AttemptScoreSummary summary, boolean includesUnselectedAiScores) {
}
