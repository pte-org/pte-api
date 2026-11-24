package com.pte.billing.internal.mapper;

import com.pte.billing.domain.enums.TenantApplicationStatus;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.tenancy.domain.Organization;

import java.time.Duration;
import java.time.Instant;

/** Presents an organization that went through the onboarding application as an application record. */
public final class TenantApplicationMapper {

    private TenantApplicationMapper() {
    }

    public static TenantApplicationResponse toResponse(Organization application) {
        return toResponse(application, null);
    }

    public static TenantApplicationResponse toResponse(Organization application, Instant responseDueAt) {
        return new TenantApplicationResponse(
                application.getPublicId(),
                application.getName(),
                application.getOrganizationType(),
                application.getCode(),
                application.getContactEmail(),
                application.getContactPhone(),
                application.getTaxCode(),
                statusOf(application).name(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getRejectReason(),
                application.getCreatedAt(),
                responseDueAt);
    }

    /** ACTIVE and SUSPENDED organizations were both approved applications. */
    public static TenantApplicationStatus statusOf(Organization application) {
        if (application.isPending()) {
            return TenantApplicationStatus.PENDING;
        }
        return application.isRejected() ? TenantApplicationStatus.REJECTED : TenantApplicationStatus.APPROVED;
    }
}
