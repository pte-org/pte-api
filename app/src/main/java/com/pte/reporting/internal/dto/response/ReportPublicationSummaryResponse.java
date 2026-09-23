package com.pte.reporting.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReportPublicationSummaryResponse(
        UUID sessionPublicId,
        UUID publicationPublicId,
        UUID publishedByPublicId,
        Instant publishedAt,
        int cohortSize,
        long publishedReportCount) {
}
