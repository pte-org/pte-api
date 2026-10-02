package com.pte.reporting.internal.dto.response;

import java.util.UUID;

public record ReportPublicationBlockerResponse(
        UUID attemptPublicId,
        UUID answerPublicId,
        String section,
        String taskType,
        String reason) {
}
