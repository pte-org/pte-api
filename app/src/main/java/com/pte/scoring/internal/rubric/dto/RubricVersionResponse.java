package com.pte.scoring.internal.rubric.dto;

import java.time.Instant;
import java.util.UUID;

/** A rubric version's metadata (its parameters are read per task type). */
public record RubricVersionResponse(
        UUID publicId,
        int versionNo,
        String status,
        int catalogVersion,
        UUID authorUserPublicId,
        UUID approverUserPublicId,
        String rejectionReason,
        Instant submittedAt,
        Instant decidedAt,
        Instant activatedAt,
        Instant retiredAt,
        long lockVersion,
        Instant createdAt,
        Instant updatedAt,
        UUID updatedByUserPublicId,
        String authorName,
        String updatedByName) {
}
