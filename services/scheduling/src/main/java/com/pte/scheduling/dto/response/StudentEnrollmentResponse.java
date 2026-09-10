package com.pte.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A student's enrollment across sessions — backs {@code admin}'s pending-exam-request transfer warning. */
public record StudentEnrollmentResponse(
        UUID enrollmentPublicId,
        UUID sessionPublicId,
        String sessionName,
        String status,
        Instant opensAt,
        Instant closesAt) {
}
