package com.pte.reporting.internal.service;

import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.internal.service.aggregation.AggregationTrace;

/**
 * A live aggregation with everything needed to explain it: the pinned context and template, the summary read off the
 * trace, and whether answers without a chosen source were counted by their AI score.
 */
public record TracedAggregation(AttemptScoreContextView context, ExamDefinitionResponse template,
                                AttemptScoreSummary summary, AggregationTrace trace,
                                boolean includesUnselectedAiScores) {
}
