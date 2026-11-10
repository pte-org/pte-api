package com.pte.reporting.internal.service;

import com.pte.reporting.internal.service.aggregation.AggregationTrace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The immutable copy of a published report. Schema 2 adds the rubric version pinned on the exam template and the full
 * aggregation trace; a schema-1 snapshot decodes with both null and has no trace.
 */
public record ReportSnapshot(
        int schemaVersion,
        UUID publicationPublicId,
        UUID publishedByPublicId,
        Instant publishedAt,
        int cohortSize,
        UUID examSnapshotPublicId,
        UUID scoreTemplatePublicId,
        Integer scoreTemplateVersion,
        AttemptScoreSummary scoreSummary,
        List<ReportSnapshotScoreInput> scoreInputs,
        UUID rubricVersionPublicId,
        AggregationTrace aggregationTrace) {
}
