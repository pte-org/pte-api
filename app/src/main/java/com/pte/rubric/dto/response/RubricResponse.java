package com.pte.rubric.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RubricResponse(
        UUID publicId,
        String rubricKey,
        String name,
        String taskTypeKey,
        String channel,
        int version,
        String status,
        UUID authorUserPublicId,
        String changeReason,
        String rejectionReason,
        String documentJson,
        String canonicalHash,
        Long entityVersion,
        Instant submittedAt,
        Instant publishedAt,
        Instant retiredAt) {
}
