package com.pte.reporting.internal.dto.response;

import java.util.List;
import java.util.UUID;

public record ReportPublicationReadinessResponse(
        UUID sessionPublicId,
        boolean sessionClosed,
        int submittedAttemptCount,
        int readyAttemptCount,
        int blockerCount,
        boolean canPublish,
        List<ReportPublicationBlockerResponse> blockers) {
}
