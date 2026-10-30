package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a grant would create, shown before the admin confirms. Exam fields are null for a student-capacity plan;
 * {@code renewsLicenseKey} is masked and set only when the grant continues an existing lane of the same plan.
 */
public record LicenseGrantPreviewResponse(
        UUID tenantId,
        UUID planId,
        String planName,
        String planType,
        List<GrantHostView> hosts,
        Instant defaultStartsAt,
        Instant startsAt,
        Instant expiresAt,
        Integer maxStudentsPerSession,
        Integer extraStudentSlots,
        String renewsLicenseKey,
        Instant renewsExpiresAt) {

    /** A current host of the organization, shown so the admin can match the contract. */
    public record GrantHostView(String username, String email, String fullName) {
    }
}
