package com.pte.reporting.internal.service;

import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.internal.service.aggregation.AggregationTrace;

import java.util.UUID;

/**
 * Cohort aggregation result paired with the exact pinned context used to calculate it, the trace the summary was read
 * from, and the rubric version pinned on the attempt's exam template (null for a legacy template without a pin).
 */
public record ReportScoreAggregation(AttemptScoreContextView context, AttemptScoreSummary summary,
                                     AggregationTrace trace, UUID rubricVersionPublicId) {

    /** Compatibility constructor for callers without a trace. */
    public ReportScoreAggregation(AttemptScoreContextView context, AttemptScoreSummary summary) {
        this(context, summary, null, null);
    }
}
