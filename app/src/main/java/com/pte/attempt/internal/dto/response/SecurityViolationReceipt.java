package com.pte.attempt.internal.dto.response;

import com.pte.attempt.domain.enums.LockdownViolationSeverity;
import com.pte.attempt.domain.enums.LockdownViolationType;

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
