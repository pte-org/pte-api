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

    public record Student(UUID publicId, String status) {
    }

    public record OrganizationContext(UUID publicId, String displayName, String organizationType) {
    }

    public record PracticeStatus(PracticeEntitlementState state) {
    }
}
