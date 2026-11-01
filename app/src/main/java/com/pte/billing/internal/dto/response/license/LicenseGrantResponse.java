package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Result of an admin grant. The bearer code is never returned; the license key is masked. Exam fields are null
 * for a student-capacity plan. {@code replayed} is true when the same Idempotency-Key returned an earlier grant.
 */
public record LicenseGrantResponse(
        UUID licenseCodePublicId,
        UUID tenantId,
        UUID planId,
        String kind,
        UUID subscriptionPublicId,
        String licenseKey,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        Integer grantedStudentSlots,
        String contractReference,
        boolean replayed) {
}
