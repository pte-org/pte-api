package com.aptis.modules.examdelivery.dto;

import java.time.OffsetDateTime;

public record RetryRequestResponse(
        Long id,
        String studentId,
        Long attemptId,
        Long examId,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        Long reviewedBy) {
}
