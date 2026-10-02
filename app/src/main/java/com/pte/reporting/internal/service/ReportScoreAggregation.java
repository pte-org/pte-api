package com.pte.reporting.internal.service;

import com.pte.attempt.dto.response.AttemptScoreContextView;

/** Cohort aggregation result paired with the exact pinned context used to calculate it. */
public record ReportScoreAggregation(AttemptScoreContextView context, AttemptScoreSummary summary) {
}
