package com.pte.proctoring.internal.dto.response;

import com.pte.proctoring.domain.enums.LockdownViolationSeverity;
import com.pte.proctoring.domain.enums.LockdownViolationType;

import java.time.Instant;
import java.util.UUID;

public record SecurityViolationReceipt(
        UUID publicId,
        UUID attemptPublicId,
        String clientEventId,
        LockdownViolationType violationType,
        LockdownViolationSeverity severity,
        Instant detectedAt,
        boolean duplicate) {
}
