package com.pte.proctoring.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SecurityAuditEntryResponse(
        UUID publicId,
        SecurityAuditSource source,
        UUID attemptPublicId,
        UUID studentPublicId,
        String violationType,
        String severity,
        Instant detectedAt,
        String clientEventId,
        String detail) {
}
