package com.pte.reporting.internal.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        List<ReportSnapshotScoreInput> scoreInputs) {
}
