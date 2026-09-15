package com.pte.enrollment.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One tenant-scoped student-roster row with optional assignment context. */
public record StudentRosterRowResponse(
        UUID studentPublicId,
        String email,
        String fullName,
        String studentCode,
        String phone,
        String status,
        Instant createdAt,
        UUID programPublicId,
        String programName,
        UUID classPublicId,
        String className) {
}
