package com.pte.practice.internal.dto.response;

import com.pte.practice.PracticeEntitlementState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PracticeEntitlementResponse(
        Student student,
        OrganizationContext organizationContext,
        List<OrganizationContext> availableOrganizations,
        PracticeStatus practice,
        Instant refreshAt) {

    /** The signed-in student, as their own account card shows them. {@code fullName} and {@code email} may be null. */
    public record Student(UUID publicId, String status, String fullName, String email) {
    }

    public record OrganizationContext(UUID publicId, String displayName, String organizationType) {
    }

    public record PracticeStatus(PracticeEntitlementState state) {
    }
}
